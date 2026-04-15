package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Taskbar;
import java.awt.Taskbar.Feature;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatLaf;
import com.queryeer.api.action.IActionRegistry;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IExpressionEvaluator;
import com.queryeer.api.service.IGraphVisualizationService;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IPayloadbuilderService;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.service.ITemplateService;
import com.queryeer.visualization.graph.GraphVisualizationService;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/** Main of Queryeer */
public class Main
{
    static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    static long time;

    /** Main */
    public static void main(String[] args) throws Exception
    {
        LOGGER.debug("VM startup time: {} ms", ManagementFactory.getRuntimeMXBean()
                .getUptime());
        time = System.currentTimeMillis();

        // Trigger expensive static initializations in background while the main thread sets up
        Thread warmup = new Thread(() ->
        {
            new ExpressionEvaluator();
            new TemplateService();
            installFlatLafs();
        }, "StartupWarmup");
        warmup.setDaemon(true);
        warmup.start();

        Thread.currentThread()
                .setUncaughtExceptionHandler(new UncaughtExceptionHandler()
                {
                    @Override
                    public void uncaughtException(Thread t, Throwable e)
                    {
                        e.printStackTrace();
                        System.exit(0);
                    }
                });
        /* Trap CMD-q on OSX to properly run shutdown hooks etc. */
        System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
        System.setProperty("apple.awt.application.name", "Queryeer");

        /* Set mnemontics for option pane */
        UIManager.put("OptionPane.yesButtonMnemonic", "89");
        UIManager.put("OptionPane.noButtonMnemonic", "78");
        UIManager.put("OptionPane.cancelButtonMnemonic", "67");

        String etcProp = System.getProperty("etc");

        if (isBlank(etcProp))
        {
            etcProp = System.getProperty("user.home");
            if (isBlank(etcProp))
            {
                throw new IllegalArgumentException("No etc folder property defined");
            }

            etcProp += "/.queryeer";
        }

        File etcFolder = new File(etcProp);
        Config config = new Config(etcFolder);

        LOGGER.debug("Config init time: {} ms", System.currentTimeMillis() - time);
        time = System.currentTimeMillis();

        String laf = config.getLookAndFeelClassName();
        try
        {
            if (isBlank(laf))
            {
                laf = UIManager.getSystemLookAndFeelClassName();
            }
            UIManager.setLookAndFeel(laf);
        }
        catch (Exception ex)
        {
            LOGGER.error("Error setting LAF: {}", laf, ex);
        }

        LOGGER.debug("LAF install time: {} ms", System.currentTimeMillis() - time);
        time = System.currentTimeMillis();

        File backupFolder = new File(etcFolder, "backup");
        if (!backupFolder.exists())
        {
            backupFolder.mkdirs();
        }
        ServiceLoader serviceLoader = new ServiceLoader();
        wire(config, backupFolder, serviceLoader);

        LOGGER.debug("Wire time: {} ms", System.currentTimeMillis() - time);
        time = System.currentTimeMillis();

        QueryeerController controller = serviceLoader.get(QueryeerController.class);
        LOGGER.debug("Controller+View+Session init time: {} ms", System.currentTimeMillis() - time);
        time = System.currentTimeMillis();

        // Start all Swing applications on the EDT.
        SwingUtilities.invokeLater(() ->
        {
            setTaskBarInfo();
            if (System.getProperty("devEnv") != null)
            {
                RepaintManager.setCurrentManager(new CheckThreadViolationRepaintManager());
            }

            LOGGER.debug("UI setVisible time: {} ms", System.currentTimeMillis() - time);
            controller.getView()
                    .setVisible(true);
        });
    }

    private static void installFlatLafs()
    {
        String method = "installLafInfo";
        try (ScanResult scanResult = new ClassGraph().enableClassInfo()
                .enableMethodInfo()
                .scan())
        {
            ClassInfoList list = scanResult.getSubclasses(FlatLaf.class)
                    .filter(c -> !c.isAbstract())
                    .filter(c -> c.hasDeclaredMethod(method));
            for (ClassInfo ci : list)
            {
                ci.loadClass()
                        .getMethod(method)
                        .invoke(null);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error installing LAF", e);
        }
    }

    private static void setTaskBarInfo()
    {
        if (!Taskbar.isTaskbarSupported())
        {
            return;
        }

        if (Taskbar.getTaskbar()
                .isSupported(Feature.ICON_IMAGE))
        {
            Taskbar.getTaskbar()
                    .setIconImage(Constants.APPLICATION_ICON_48);
        }
    }

    private static void wire(Config config, File backupFolder, ServiceLoader serviceLoader) throws IOException, InstantiationException, IllegalAccessException
    {
        // CSOFF
        long t = System.currentTimeMillis();
        // CSON

        serviceLoader.register(serviceLoader);

        // Service
        EventBus eventBus = new EventBus();
        serviceLoader.register(IEventBus.class, eventBus);
        serviceLoader.register(eventBus);

        CryptoService cryptoService = new CryptoService(serviceLoader);
        serviceLoader.register(ICryptoService.class, cryptoService);
        LOGGER.debug("Wire: CryptoService: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        serviceLoader.register(ITemplateService.class, new TemplateService());
        LOGGER.debug("Wire: TemplateService: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        serviceLoader.register(IConfig.class, config);
        serviceLoader.register(config);

        ActionRegistry actionRegistry = new ActionRegistry();
        serviceLoader.register(IActionRegistry.class, actionRegistry);

        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
        serviceLoader.register(IExpressionEvaluator.class, expressionEvaluator);
        LOGGER.debug("Wire: ExpressionEvaluator: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        FileWatchService watchService = new FileWatchService();
        serviceLoader.register(watchService);
        LOGGER.debug("Wire: FileWatchService: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        PayloadbuilderService payloadbuilderService = new PayloadbuilderService();
        serviceLoader.register(IPayloadbuilderService.class, payloadbuilderService);
        serviceLoader.register(IGraphVisualizationService.class, new GraphVisualizationService());
        LOGGER.debug("Wire: PayloadbuilderService: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        // UI
        QueryeerModel queryeerModel = new QueryeerModel(config, backupFolder, watchService);
        serviceLoader.register(IQueryFileProvider.class, queryeerModel);
        serviceLoader.register(queryeerModel);
        serviceLoader.register(QueryeerView.class);
        serviceLoader.register(QueryeerController.class);
        serviceLoader.register(QueryFileTabbedPane.class);
        serviceLoader.register(ProjectsView.class);
        serviceLoader.register(DialogFactory.class, List.of(IDialogFactory.class));
        serviceLoader.register(IIconFactory.class, new IconFactory());
        LOGGER.debug("Wire: Core services registered: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        PluginHandler pluginHandler = new PluginHandler(config);
        LOGGER.debug("Wire: PluginHandler (classloaders): {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        // Inject plugins last
        File classgraphCache = new File(config.getEtcFolder(), "queryeer.classgraph.cache");
        serviceLoader.injectExtensions(pluginHandler, classgraphCache);
        LOGGER.debug("Wire: ClassGraph scan + inject: {} ms", System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        // Initalize config with all query engines
        config.init(serviceLoader.getAll(IQueryEngine.class));
        LOGGER.debug("Wire: config.init (query engines): {} ms", System.currentTimeMillis() - t);

        Runtime.getRuntime()
                .addShutdownHook(new Thread(() -> watchService.close()));
    }

    static class CheckThreadViolationRepaintManager extends RepaintManager
    {
        // it is recommended to pass the complete check
        private boolean completeCheck = true;

        public boolean isCompleteCheck()
        {
            return completeCheck;
        }

        public void setCompleteCheck(boolean completeCheck)
        {
            this.completeCheck = completeCheck;
        }

        @Override
        public synchronized void addInvalidComponent(JComponent component)
        {
            checkThreadViolations(component);
            super.addInvalidComponent(component);
        }

        @Override
        public void addDirtyRegion(JComponent component, int x, int y, int w, int h)
        {
            checkThreadViolations(component);
            super.addDirtyRegion(component, x, y, w, h);
        }

        private void checkThreadViolations(JComponent c)
        {
            if (!SwingUtilities.isEventDispatchThread()
                    && (completeCheck
                            || c.isShowing()))
            {
                Exception exception = new Exception();
                boolean repaint = false;
                boolean fromSwing = false;
                StackTraceElement[] stackTrace = exception.getStackTrace();
                for (StackTraceElement st : stackTrace)
                {
                    if (repaint
                            && st.getClassName()
                                    .startsWith("javax.swing."))
                    {
                        fromSwing = true;
                    }
                    if ("repaint".equals(st.getMethodName()))
                    {
                        repaint = true;
                    }
                }
                if (repaint
                        && !fromSwing)
                {
                    // no problems here, since repaint() is thread safe
                    return;
                }
                LOGGER.error("EDT fail", exception);
            }
        }
    }
}
