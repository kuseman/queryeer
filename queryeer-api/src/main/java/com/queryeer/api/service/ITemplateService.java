package com.queryeer.api.service;

import java.util.Map;

/** Definition of a template service to turn tempaltes into strings */
public interface ITemplateService
{
    /** Process template with provided model */
    default String process(String name, String template, Map<String, Object> model)
    {
        return process(name, template, model, false);
    }

    /** Process template with provided model. */
    String process(String name, String template, Map<String, Object> model, boolean throwErrors);
}
