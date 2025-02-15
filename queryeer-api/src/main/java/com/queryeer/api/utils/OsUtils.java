package com.queryeer.api.utils;

/** Utility class for os related operations. */
public class OsUtils
{
    public static final int UNKNOWN = -1;
    public static final int WINDOWS = 0;
    public static final int LINUX = 1;
    public static final int MAC_OSX = 2;

    private static final int OS;

    static
    {
        String osname = System.getProperty("os.name");

        int os = UNKNOWN;
        if (osname != null)
        {
            osname = osname.toLowerCase();
            if (osname.contains("windows"))
            {
                os = WINDOWS;
            }
            else if (osname.contains("mac os x"))
            {
                os = MAC_OSX;
            }
            else if (osname.contains("linux"))
            {
                os = LINUX;
            }
            else
            {
                os = UNKNOWN;
            }
        }
        OS = os;
    }

    private OsUtils()
    {
    }

    public static boolean isMacOsx()
    {
        return OS == MAC_OSX;
    }

    public static boolean isLinux()
    {
        return OS == LINUX;
    }

    public static boolean isWindows()
    {
        return OS == WINDOWS;
    }
}
