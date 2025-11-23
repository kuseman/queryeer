package com.queryeer;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Testing of templates. */
public class TemplateServiceTest
{
    @Test
    @Disabled
    void test()
    {
        TemplateService ts = new TemplateService();

        System.out.println(ts.process("test", """
                <#list catalogs?filter(x -> x.name == 'ESCatalog' && x.default == true) as x>
                  ${x.alias}
                </#list>
                """, Map.of("catalogs", List.of(Map.of("alias", "es", "name", "ESCatalog", "default", true)), "tableRow", Map.of("cell", Map.of("value", 123, "header", "title")))));
    }
}
