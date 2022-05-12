package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.queryeer.api.service.IConfig;
import com.queryeer.api.service.IQueryFileProvider;

/** Main of Queryeer */
public class Main
{
    /** Main */
    public static void main(String[] args) throws Exception
    {
        String etcProp = System.getProperty("etc");

        if (isBlank(etcProp))
        {
            throw new IllegalArgumentException("No etc folder property defined");
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

        Config config = new Config(etcFolder);
        serviceLoader.register(IConfig.class, config);
        serviceLoader.register(config);

        QueryFileProvider queryFileProvider = new QueryFileProvider();
        serviceLoader.register(IQueryFileProvider.class, queryFileProvider);
        serviceLoader.register(queryFileProvider);

        serviceLoader.injectExtensions();

        config.loadCatalogExtensions(serviceLoader);

        // Start all Swing applications on the EDT.
        SwingUtilities.invokeLater(() ->
        {
            QueryeerView view = new QueryeerView();
            QueryeerModel model = new QueryeerModel();
            new QueryeerController(serviceLoader, view, model);
            view.setVisible(true);
        });
    }
}
