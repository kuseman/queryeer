package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.queryeer.api.action.IActionRegistry;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.ICryptoService;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IQueryFileProvider;
import com.queryeer.api.service.ITemplateService;

/** Main of Queryeer */
public class Main
{
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

        QueryeerController controller = serviceLoader.get(QueryeerController.class);

        // Start all Swing applications on the EDT.
        SwingUtilities.invokeLater(() ->
        {
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
        serviceLoader.register(ITemplateService.class, new TemplateService());

        Config config = new Config(etcFolder);
        serviceLoader.register(IConfig.class, config);
        serviceLoader.register(config);

        QueryFileProvider queryFileProvider = new QueryFileProvider();
        serviceLoader.register(IQueryFileProvider.class, queryFileProvider);
        serviceLoader.register(queryFileProvider);

        ActionRegistry actionRegistry = new ActionRegistry();
        serviceLoader.register(IActionRegistry.class, actionRegistry);

        // UI
        serviceLoader.register(new QueryeerModel());
        serviceLoader.register(QueryeerView.class);
        serviceLoader.register(QueryeerController.class);
        serviceLoader.register(OptionsDialog.class);
        serviceLoader.register(QueryFileTabbedPane.class);
        serviceLoader.register(QueryFileViewFactory.class);

        serviceLoader.register(IIconFactory.class, new IconFactory());

        // Inject plugins last
        serviceLoader.injectExtensions();

        // Initalize config with all query engines
        config.init(serviceLoader.getAll(IQueryEngine.class));
    }
}
