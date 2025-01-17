package com.queryeer;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.service.ITemplateService;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/** Templating */
class TemplateService implements ITemplateService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateService.class);
    private static final Configuration FTL_CONFIG;
    static
    {
        FTL_CONFIG = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        FTL_CONFIG.setDefaultEncoding("UTF-8");
        FTL_CONFIG.setLocale(Locale.US);
        FTL_CONFIG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /** Process template with provided model. */
    @Override
    public String process(String name, String template, Map<String, Object> model, boolean throwErrors)
    {
        try
        {
            Template ftl = new Template(name, new StringReader(template), FTL_CONFIG);
            Writer out = new StringWriter();
            ftl.process(model, out);
            return out.toString();
        }
        catch (Exception e)
        {
            if (throwErrors)
            {
                throw e instanceof RuntimeException re ? re
                        : new RuntimeException(e);
            }
            LOGGER.error("Error generating template {}", name, e);
            return "";
        }
    }
}
