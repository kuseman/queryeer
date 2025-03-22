package com.queryeer;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Watch service that wraps {@link Watchable} with a callback style api. */
class FileWatchService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWatchService.class);
    /* Map with the watched folder as key and consumers as key */
    private final Map<Path, WatchEntry> map = new ConcurrentHashMap<>();

    private final Thread thread;
    private final WatchService watchService;

    FileWatchService()
    {
        thread = new Thread(fileWatcherRunnable);
        thread.setName("QueryeerFileWatchService");
        thread.setDaemon(true);
        thread.start();

        WatchService watchService = null;
        try
        {
            watchService = FileSystems.getDefault()
                    .newWatchService();
        }
        catch (IOException e)
        {
            LOGGER.error("Error creating watch service", e);
        }
        this.watchService = watchService;
    }

    void close()
    {
        thread.interrupt();
        try
        {
            watchService.close();
        }
        catch (IOException e)
        {
            LOGGER.error("Error closing watchservice", e);
        }
    }

    private static class WatchEntry
    {
        private final Path folder;
        private volatile WatchKey watchKey;

        WatchEntry(Path folder, WatchKey watchKey)
        {
            this.folder = folder;
            this.watchKey = watchKey;
        }

        /* Listeners for the watched folder. */
        private List<FileWatchListener> listeners;

        /* Listeners for watched files inside watched folder */
        private Map<Path, List<FileWatchListener>> fileListeners;
    }

    /**
     * Register a path that should be watched. NOTE! Listener is called from another thread.
     */
    void register(Path path, FileWatchListener listener)
    {
        if (watchService == null)
        {
            return;
        }

        // We always watch folders, so move up to parent if needed
        boolean isDirectory = Files.isDirectory(path);
        Path folder = isDirectory ? path
                : path.getParent();
        Path file = isDirectory ? null
                : path;

        if (folder == null
                || !Files.exists(folder))
        {
            return;
        }

        map.compute(folder, (k, v) ->
        {
            WatchEntry entry = v;
            if (entry == null)
            {
                entry = new WatchEntry(folder, registerPath(folder));
            }

            if (file == null)
            {
                if (entry.listeners == null)
                {
                    entry.listeners = new ArrayList<>();
                }
                entry.listeners.add(listener);
            }
            else
            {
                if (entry.fileListeners == null)
                {
                    entry.fileListeners = new ConcurrentHashMap<>();
                }
                entry.fileListeners.computeIfAbsent(file, f -> new ArrayList<>())
                        .add(listener);
            }

            return entry;
        });
    }

    /** Unregister path from watcher */
    void unregister(FileWatchListener listener)
    {
        synchronized (map)
        {
            Iterator<Entry<Path, WatchEntry>> it = map.entrySet()
                    .iterator();
            while (it.hasNext())
            {
                Entry<Path, WatchEntry> entry = it.next();
                WatchEntry watchEntry = entry.getValue();
                if (watchEntry.listeners != null)
                {
                    watchEntry.listeners.remove(listener);
                }
                if (watchEntry.fileListeners != null)
                {
                    Iterator<Entry<Path, List<FileWatchListener>>> it2 = watchEntry.fileListeners.entrySet()
                            .iterator();
                    while (it2.hasNext())
                    {
                        List<FileWatchListener> list = it2.next()
                                .getValue();
                        list.remove(listener);
                        if (list.isEmpty())
                        {
                            it2.remove();
                        }
                    }
                }

                if (CollectionUtils.isEmpty(watchEntry.listeners)
                        && MapUtils.isEmpty(watchEntry.fileListeners)
                        && watchEntry.watchKey != null)
                {
                    LOGGER.debug("Removed watch key for {}", entry.getKey());
                    watchEntry.watchKey.cancel();
                    it.remove();
                }
            }
        }
    }

    private WatchKey registerPath(Path path)
    {
        // Watch service not created, nothing to do
        if (watchService == null)
        {
            return null;
        }

        try
        {
            LOGGER.debug("Register {}", path);
            return path.register(watchService, StandardWatchEventKinds.OVERFLOW, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        }
        catch (IOException e)
        {
            LOGGER.error("Error register path '{}' to watchservice", path, e);
            return null;
        }
    }

    private Runnable fileWatcherRunnable = () ->
    {
        long threshold = 500;
        long lastProcessing = -1;

        while (!Thread.interrupted())
        {
            // Sleep a while if there was very few events
            if (lastProcessing > 0
                    && System.currentTimeMillis() - lastProcessing < threshold)
            {
                ThreadUtils.sleepQuietly(Duration.ofMillis(threshold));
            }
            lastProcessing = System.currentTimeMillis();

            for (WatchEntry entry : map.values())
            {
                try
                {
                    // Try to register
                    if (entry.watchKey == null)
                    {
                        entry.watchKey = registerPath(entry.folder);
                        continue;
                    }

                    List<WatchEvent<?>> events = entry.watchKey.pollEvents();
                    if (events.isEmpty())
                    {
                        continue;
                    }

                    for (WatchEvent<?> event : events)
                    {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW)
                        {
                            continue;
                        }

                        Path path = (Path) event.context();
                        // Construct full path if not
                        if (path.getParent() == null)
                        {
                            path = entry.folder.resolve(path);
                        }

                        LOGGER.debug("Event: parent:'{}' path:'{}' kind: '{}' time: '{}'", entry.folder, path, event.kind(), System.currentTimeMillis());
                        // Call folder listeners
                        if (entry.listeners != null)
                        {
                            for (FileWatchListener listener : entry.listeners)
                            {
                                listener.pathChanged(path, event.kind());
                            }
                        }

                        // Call file listeners
                        if (entry.fileListeners != null)
                        {
                            List<FileWatchListener> list = entry.fileListeners.get(path);
                            if (list != null)
                            {
                                for (FileWatchListener listener : list)
                                {
                                    listener.pathChanged(path, event.kind());
                                }
                            }
                        }
                    }

                    // Watch key not valid anymore, re-register
                    if (!entry.watchKey.reset())
                    {
                        entry.watchKey = registerPath(entry.folder);
                    }
                }
                catch (Exception e)
                {
                    LOGGER.error("Error processing watch service for path {}", entry.folder, e);
                }
            }
        }
    };

    interface FileWatchListener
    {
        void pathChanged(Path path, Kind<?> kind);
    }
}
