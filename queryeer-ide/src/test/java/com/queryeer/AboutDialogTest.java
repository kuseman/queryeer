package com.queryeer;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class AboutDialogTest
{
    public static void main(String[] args)
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
        new AboutDialog(null, "1.0.0").setVisible(true);
    }
}
