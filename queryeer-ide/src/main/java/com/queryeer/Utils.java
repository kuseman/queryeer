package com.queryeer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;

/** Variuos utils used by Queryeer */
final class Utils
{
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.)?");
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /** Get HEX represenatation of a byte arrya */
    static String bytesToHex(byte[] bytes)
    {
        // CSOFF
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++)
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        // CSON
        return "0x" + new String(hexChars);
    }

    /** Compare 2 semver strings */
    static int compareVersions(String nameA, String nameB)
    {
        int[] componentsA = getVersionComponents(nameA);
        int[] componentsB = getVersionComponents(nameB);
        for (int i = 0; i < 3; i++)
        {
            int c = Integer.compare(componentsA[i], componentsB[i]);
            if (c != 0)
            {
                return -1 * c;
            }
        }

        return 0;
    }

    /**
     * Tries to find 3 semver components from provided string value. Returns a 3 item int array with found components
     */
    private static int[] getVersionComponents(String value)
    {
        int[] components = new int[] { -1, -1, -1 };
        Matcher matcher = VERSION_PATTERN.matcher(value);
        int index = 0;
        while (matcher.find())
        {
            components[index++] = Integer.parseInt(matcher.group(1));
            if (index == 3)
            {
                break;
            }
        }
        return components;
    }

    /** Fetches latest version (tag) from github */
    static String getLatestTag()
    {
        HttpURLConnection c = null;
        try
        {
            URL u = new URL("https://api.github.com/repos/kuseman/queryeer/releases");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(250);
            c.setReadTimeout(5000);
            c.connect();
            int status = c.getResponseCode();

            if (status != 200)
            {
                return null;
            }

            try (InputStream is = c.getInputStream())
            {
                return QueryeerController.MAPPER.readValue(is, new TypeReference<List<Map<String, Object>>>()
                {
                })
                        .stream()
                        .map(m -> (String) m.get("tag_name"))
                        .sorted(Utils::compareVersions)
                        .findAny()
                        .orElse(null);
            }
        }
        catch (Exception e)
        {
            System.err.println("Error fetching latest tags");
        }
        finally
        {
            if (c != null)
            {
                try
                {
                    c.disconnect();
                }
                catch (Exception ex)
                {
                    System.err.println("Error fetching latest tags");
                }
            }
        }
        return null;
    }
}
