package com.queryeer;

import java.io.File;
import java.io.IOException;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

class AboutDialogTest
{
    public static void main(String[] args) throws IOException
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

        AboutDialog d = new AboutDialog(null, "1.0.0", new File("/etc"), new File("/shared"));
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setVisible(true);
    }
}
