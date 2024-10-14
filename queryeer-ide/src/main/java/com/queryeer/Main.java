package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.extensions.catalog.ICatalogExtensionFactory;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.completion.CompletionInstaller;
import com.queryeer.component.CatalogExtensionViewFactory;

/** Main of Queryeer */
public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    /** Main */
    public static void main(String[] args) throws Exception
    {
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

        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex)
        // CSOFF
        {
            ex.printStackTrace();
        }
        // CSON
        File etcFolder = new File(etcProp);
        ServiceLoader serviceLoader = new ServiceLoader();
        wire(etcFolder, serviceLoader);

        List<ICatalogExtensionFactory> catalogExtensionFactories = serviceLoader.getAll(ICatalogExtensionFactory.class);
        Config config = serviceLoader.get(Config.class);
        config.loadCatalogExtensions(catalogExtensionFactories);

        QueryeerController controller = serviceLoader.get(QueryeerController.class);

        // Start all Swing applications on the EDT.
        SwingUtilities.invokeLater(() ->
        {
            if (System.getProperty("devEnv") != null)
            {
                RepaintManager.setCurrentManager(new CheckThreadViolationRepaintManager());
            }
            controller.getView()
                    .setVisible(true);
        });
    }

    private static void wire(File etcFolder, ServiceLoader serviceLoader) throws IOException, InstantiationException, IllegalAccessException
    {
        serviceLoader.register(serviceLoader);

        EventBus eventBus = new EventBus();
        serviceLoader.register(IEventBus.class, eventBus);
        serviceLoader.register(eventBus);

        CryptoService cryptoService = new CryptoService(serviceLoader);
        serviceLoader.register(ICryptoService.class, cryptoService);

        Config config = new Config(etcFolder);
        serviceLoader.register(IConfig.class, config);
        serviceLoader.register(config);

        QueryFileProvider queryFileProvider = new QueryFileProvider();
        serviceLoader.register(IQueryFileProvider.class, queryFileProvider);
        serviceLoader.register(queryFileProvider);

        // UI
        serviceLoader.register(new QueryeerModel());
        serviceLoader.register(QueryeerView.class);
        serviceLoader.register(QueryeerController.class);
        serviceLoader.register(OptionsDialog.class);
        serviceLoader.register(QueryFileTabbedPane.class);
        serviceLoader.register(QueryFileViewFactory.class);
        serviceLoader.register(CatalogExtensionViewFactory.class);
        serviceLoader.register(new CompletionInstaller(eventBus));
        serviceLoader.register(IIconFactory.class, new IconFactory());

        // Inject plugins last
        serviceLoader.injectExtensions();
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
