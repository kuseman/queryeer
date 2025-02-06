package com.queryeer;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLaf;

/** UI Utils. */
public class UiUtils
{
    /** Checks if current LAF is dark. */
    public static boolean isDarkLookAndFeel()
    {
        return FlatLaf.isLafDark()
                || UIManager.getLookAndFeel()
                        .getName()
                        .toLowerCase()
                        .contains("dark");
    }
}
