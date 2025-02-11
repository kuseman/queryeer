package com.queryeer;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.SplashScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Splash screen handling. */
class Splash
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Splash.class);
    private static SplashScreen splashScreen;
    private static Graphics2D splashGraphics;

    static
    {
        splashScreen = SplashScreen.getSplashScreen();
        splashGraphics = splashScreen != null ? splashScreen.createGraphics()
                : null;

        LOGGER.info("Splash iniizlied: {}", splashGraphics != null);
    }

    static void dispose()
    {
        if (splashGraphics == null)
        {
            return;
        }

        splashGraphics.dispose();
        splashScreen.close();
    }

    static void init()
    {
        if (splashGraphics == null)
        {
            System.out.println("Init splash: null");
            return;
        }

        String version;
        if (!isBlank(QueryeerView.class.getPackage()
                .getImplementationVersion()))
        {
            version = QueryeerView.class.getPackage()
                    .getImplementationVersion();
        }
        else
        {
            version = "Dev";
        }

        String fullVersionString = "Version: " + version;
        splashGraphics.setFont(splashGraphics.getFont()
                .deriveFont(Font.BOLD));

        int width = splashGraphics.getFontMetrics()
                .stringWidth(fullVersionString);

        // Right bound of white bar = 470

        splashGraphics.setColor(Color.BLACK);
        splashGraphics.drawString(fullVersionString, 470 - width, 255);

        splashScreen.update();
    }

    static void updateProgress(String text)
    {
        if (splashGraphics == null)
        {
            return;
        }

        splashGraphics.setComposite(AlphaComposite.Clear);
        splashGraphics.setPaintMode();
        splashGraphics.setColor(new Color(199, 199, 199));
        splashGraphics.fillRect(9, 243, 300, 14);
        splashGraphics.setColor(Color.BLACK);
        splashGraphics.setFont(splashGraphics.getFont()
                .deriveFont(Font.BOLD));
        splashGraphics.drawString(text, 10, 255);
        splashScreen.update();
    }
}
