package com.queryeer;

import static java.util.Arrays.asList;

import com.queryeer.api.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

/** Test of {@link PluginHandler} */
public class UtilsTest extends Assert
{
    @Test
    public void test_plugin_version_comparator_sorts_newer_versions_first() throws IOException
    {
        List<File> plugins = asList(new File("pluginA-0.2.1"), new File("pluginA-0.10.0"), new File("pluginB"), new File("pluginA-2"), new File("pluginA-0.11"));
        plugins.sort((a, b) -> Utils.compareVersions(a.getName(), b.getName()));

        assertEquals(asList(new File("pluginA-2"), new File("pluginA-0.11"), new File("pluginA-0.10.0"), new File("pluginA-0.2.1"), new File("pluginB")), plugins);
    }

    @Test
    public void test_when_needle_is_not_in_haystack_then_return_false()
    {
        assertFalse(StringUtils.findStringsInString("", ""));
        assertFalse(StringUtils.findStringsInString("abc1mno", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("checkoutprod", "EComCustomerMasterProd"));
        assertFalse(StringUtils.findStringsInString("ee", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("q", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("sadf", ""));
        assertFalse(StringUtils.findStringsInString("xxx", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("za", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("acb", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("opab", "abcdefghijklmnop"));
        assertFalse(StringUtils.findStringsInString("customemasttest", "SupplyChainTest - Monitoring -BUS_CustomersServiceTest"));
        assertFalse(StringUtils.findStringsInString("customemasttest", "SupplyChainTest - Monitoring -BUS_CustomersValidationTest"));
    }

    @Test
    public void test_when_needle_is_in_haystack_then_return_true()
    {
        assertTrue(StringUtils.findStringsInString("cud", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("cust pr t", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("cust pr maste d", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("cust prod mast", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("custprodmast", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("custprod", "EComCustomerMasterProd"));

        assertTrue(StringUtils.findStringsInString("efgbcd", "abcdefghijklmnop"));
        assertTrue(StringUtils.findStringsInString("custprodmast", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("custprod", "EComCustomerMasterProd"));
        assertTrue(StringUtils.findStringsInString("abcmno", "abcdefghijklmnop"));
        assertTrue(StringUtils.findStringsInString("", "abcdefghijklmnop"));
        assertTrue(StringUtils.findStringsInString("abc", "abcdefghijklmnop"));
        assertTrue(StringUtils.findStringsInString("DeF", "abcdefghijklmnop"));
        assertTrue(StringUtils.findStringsInString("EFG", "abcdefghijklmnop"));
        assertTrue(StringUtils.findStringsInString("efgcd", "abcdefghijklmnop"));
    }

    @Test
    public void test_when_100000_searches_are_performed_be_quick_about_it()
    {
        long startTime = System.currentTimeMillis();
        Random r = new Random();
        String haystack = "Some string To Test Performance with";
        String needle = "needleschmeedle";
        for (int i = 0; i < 100000; i++)
        {
            needle = (needle + (char) (r.nextInt(26) + 'a')).substring(0, 10);
            haystack = (haystack + (char) (r.nextInt(26) + 'a')).substring(0, 20);
            boolean found = StringUtils.findStringsInString(needle, haystack);
        }
        long duration = (System.currentTimeMillis() - startTime);
        assertFalse("Performance test took too long: " + duration + "ms", duration > 1000);
    }
}
