package com.queryeer;

import java.io.IOException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IEventBus;
import com.queryeer.api.service.IQueryFileProvider;

/** Main of Queryeer */
public class Main
{
    /** Main */
    public static void main(String[] args) throws Exception
    {
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
        ServiceLoader serviceLoader = new ServiceLoader();
        wire(serviceLoader);

        QueryeerController controller = serviceLoader.get(QueryeerController.class);

        // Start all Swing applications on the EDT.
        SwingUtilities.invokeLater(() ->
        {
            controller.getView()
                    .setVisible(true);
        });
    }

    private static void wire(ServiceLoader serviceLoader) throws IOException, InstantiationException, IllegalAccessException
    {
        serviceLoader.register(serviceLoader);

        EventBus eventBus = new EventBus();
        serviceLoader.register(IEventBus.class, eventBus);
        serviceLoader.register(eventBus);

        Config config = new Config();
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
        //

        // Inject plugins last
        serviceLoader.injectExtensions();
    }

}
