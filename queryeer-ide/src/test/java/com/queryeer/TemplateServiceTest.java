package com.queryeer;

import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

/** Testing of templates. */
public class TemplateServiceTest
{
    @Test
    @Ignore
    public void test()
    {
        TemplateService ts = new TemplateService();

        System.out.println(ts.process("test", """
                <#list catalogs?filter(x -> x.name == 'ESCatalog' && x.default == true) as x>
                  ${x.alias}
                </#list>
                """, Map.of("catalogs", List.of(Map.of("alias", "es", "name", "ESCatalog", "default", true)), "tableRow", Map.of("cell", Map.of("value", 123, "header", "title")))));
    }
}
