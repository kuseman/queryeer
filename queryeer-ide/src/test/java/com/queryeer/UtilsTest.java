package com.queryeer;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
}
