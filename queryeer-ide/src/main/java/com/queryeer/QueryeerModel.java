package com.queryeer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.Config.QueryeerSession;
import com.queryeer.Config.QueryeerSessionFile;
import com.queryeer.FileWatchService.FileWatchListener;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.service.IQueryFileProvider;

/** Queryeer model */
class QueryeerModel implements IQueryFileProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryeerModel.class);
    public static final String FILES = "files";
    public static final String ORDER = "order";
    public static final String SELECTED_FILE = "selectedFile";
    public static final String SELECTED_FILE_ALTERED = "selectedFileAltered";
    private static final int FILEMAINTENANCEINTERVAL = 20 * 1000;
    private final Config config;
    private final File backupPath;
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);
    private final List<QueryFileModel> files = new ArrayList<>();
    private final List<QueryFileModel> removedFiles = new ArrayList<>();
    private final FileWatchService watchService;
    private QueryFileModel selectedFile;
    private QueryFileModel previousSelectedFile;
    private Thread maintenanceThread;

    QueryeerModel(Config config, File backupPath, FileWatchService watchService)
    {
        this.config = requireNonNull(config);
        this.backupPath = requireNonNull(backupPath);
        this.watchService = requireNonNull(watchService);
    }

    /** Init model. Start maintenance task etc. */
    void init()
    {
        maintenanceThread = new Thread(() ->
        {
            while (!Thread.interrupted())
            {
                try
                {
                    Thread.sleep(FILEMAINTENANCEINTERVAL);
                }
                catch (InterruptedException e)
                {
                    break;
                }

                performBackupTask();
            }
        });
        maintenanceThread.setName("QueryeerBackupMaintenance");
        // No daemon here to left this run to completion upon close to not write half
        // files etc.
        maintenanceThread.setDaemon(false);
        maintenanceThread.start();

        if (config.getSession().previousActiveIndex >= 0
                && config.getSession().previousActiveIndex < files.size())
        {
            previousSelectedFile = files.get(config.getSession().previousActiveIndex);
        }
    }

    void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    void addFile(QueryFileModel file, boolean forceLast)
    {
        int index;
        if (forceLast
                || config.isOpenNewFilesLast())
        {
            index = files.size();
        }
        else
        {
            // Add new file after selected one
            index = files.indexOf(selectedFile);
            if (index < 0)
            {
                index = files.size();
            }
            else
            {
                index++;
            }
        }

        register(file);
        files.add(index, file);

        pcs.fireIndexedPropertyChange(FILES, index, null, file);

        setSelectedFile(file);
    }

    private void register(QueryFileModel file)
    {
        if (!file.isNew())
        {
            Path path = file.getFile()
                    .toPath();
            if (file.watchListener != null)
            {
                watchService.unregister(file.watchListener);
            }
            FileWatchListener listener = (p, kind) ->
            {
                if (selectedFile != null
                        && p.compareTo(selectedFile.getFile()
                                .toPath()) == 0
                        && (kind == StandardWatchEventKinds.ENTRY_MODIFY
                                || kind == StandardWatchEventKinds.ENTRY_DELETE))
                {
                    SwingUtilities.invokeLater(() -> pcs.firePropertyChange(SELECTED_FILE_ALTERED, null, selectedFile));
                }
            };
            file.watchListener = listener;
            watchService.register(path, listener);
        }
    }

    void removeFile(QueryFileModel file)
    {
        int index = files.indexOf(file);
        if (index == -1)
        {
            return;
        }

        if (file.watchListener != null)
        {
            watchService.unregister(file.watchListener);
            file.watchListener = null;
        }

        file.dispose();

        if (files.remove(index) == previousSelectedFile)
        {
            previousSelectedFile = null;
        }
        // Prev will be re-set upon property change so store it before firing
        QueryFileModel prevFile = previousSelectedFile;

        pcs.fireIndexedPropertyChange(FILES, index, file, null);

        removedFiles.add(file);
        QueryFileModel selectedFile;
        // Select previous or the removed files index
        int prevIndex = files.indexOf(prevFile);
        index = prevIndex < 0 ? index
                : prevIndex;
        // .. else select the last file
        if (index < 0
                || index >= files.size())
        {
            index = files.size() - 1;
        }
        selectedFile = index >= 0
                && index < files.size() ? files.get(index)
                        : null;

        setSelectedFile(selectedFile);
    }

    void setSelectedFile(QueryFileModel file)
    {
        if (!Objects.equals(selectedFile, file))
        {
            file.bumpActivity();
            QueryFileModel old = selectedFile;
            previousSelectedFile = selectedFile;
            selectedFile = file;
            pcs.firePropertyChange(SELECTED_FILE, old, selectedFile);
        }
    }

    void sortFiles(Comparator<QueryFileModel> comparator)
    {
        files.sort(comparator);
        pcs.firePropertyChange(ORDER, null, null);
    }

    void fileSaved(QueryFileModel file)
    {
        register(file);
    }

    @Override
    public IQueryFile getCurrentFile()
    {
        return selectedFile;
    }

    QueryFileModel getSelectedFile()
    {
        return selectedFile;
    }

    List<QueryFileModel> getFiles()
    {
        return files;
    }

    /** Tries to select provided file if in model. Returns true if found */
    boolean select(String file)
    {
        for (QueryFileModel model : files)
        {
            if (model.getFile()
                    .getAbsolutePath()
                    .equalsIgnoreCase(file))
            {
                setSelectedFile(model);
                return true;
            }
        }
        return false;
    }

    void close()
    {
        for (QueryFileModel file : files)
        {
            file.dispose();
        }

        maintenanceThread.interrupt();
        while (maintenanceThread.isAlive())
        {
            ThreadUtils.sleepQuietly(Duration.ofMillis(50));
        }
        // Make backups etc. before exit
        performBackupTask();

        // Delete all backup files in backup folder
        // that isn't in config
        Set<File> actualBackupFiles = files.stream()
                .filter(f -> f.backupFile != null)
                .map(f -> f.backupFile)
                .collect(toSet());
        File[] files = backupPath.listFiles();
        int length = files.length;
        for (int i = 0; i < length; i++)
        {
            if (!actualBackupFiles.contains(files[i]))
            {
                files[i].delete();
            }
        }

        try
        {
            QueryFileModel.DISPOSE_EXECUTOR.shutdown();
            QueryFileModel.DISPOSE_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
        }
    }

    private void performBackupTask()
    {
        LOGGER.debug("Running backup task");

        QueryeerSession session = config.getSession();
        List<QueryeerSessionFile> sessionFiles = new ArrayList<>();
        int activeFileIndex = 0;
        int previousSelectedFileIndex = files.indexOf(previousSelectedFile);

        List<QueryFileModel> files = new ArrayList<>(this.files);

        // NOTE! Need to make a copy here since the list might
        // be altered from EDT when this is running from thread
        for (QueryFileModel file : files)
        {
            // User have saved the file without any edit
            // This typically happens when a backup is used upon load of Queryeer
            // and the first operation the user do is save
            // then backupdirty is not set (since we have just loaded it without eny edit)
            if (!file.backupDirty
                    && !file.isDirty()
                    && file.backupFile != null)
            {
                file.backupFile.delete();
                file.backupFile = null;
                file.backupDirty = false;
            }

            if (file.backupDirty)
            {
                try
                {
                    if (file.backupFile == null)
                    {
                        String backupFileName = file.getFile()
                                .getName() + "_" + System.currentTimeMillis();
                        file.backupFile = new File(this.backupPath, backupFileName);
                    }

                    // File had backup dirty state but is not dirty anymore
                    // => user saved file, then remove the backup file and reset state
                    if (!file.isDirty())
                    {
                        file.backupFile.delete();
                        file.backupFile = null;
                        file.backupDirty = false;
                    }
                    else
                    {
                        file.getEditor()
                                .saveToFile(file.backupFile, false);
                        file.backupDirty = false;
                    }
                }
                catch (Exception e)
                {
                    LOGGER.error("Error saving backup file {}", file.getFile(), e);
                }
            }

            QueryeerSessionFile sessionFile = new Config.QueryeerSessionFile();
            sessionFile.file = file.getFile();
            sessionFile.backupFile = file.backupFile;
            sessionFile.isNew = file.isNew();
            sessionFiles.add(sessionFile);
        }

        QueryFileModel selectedFile = this.selectedFile;
        if (selectedFile != null)
        {
            activeFileIndex = files.indexOf(selectedFile);
        }
        if (activeFileIndex != session.activeFileIndex
                || previousSelectedFileIndex != session.previousActiveIndex
                || !sessionFiles.equals(session.files))
        {
            session.activeFileIndex = activeFileIndex;
            session.previousActiveIndex = previousSelectedFileIndex;
            session.files.clear();
            session.files.addAll(sessionFiles);
            config.saveSession();
        }
        for (QueryFileModel file : new ArrayList<>(removedFiles))
        {
            try
            {
                if (file.backupFile != null)
                {
                    file.backupFile.delete();
                }
            }
            catch (Exception e)
            {
                LOGGER.error("Error removing backup file {}", file.getFile(), e);
            }
        }
        removedFiles.clear();

        LOGGER.debug("Finnished running backup task");
    }
}
