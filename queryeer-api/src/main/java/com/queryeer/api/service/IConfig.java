package com.queryeer.api.service;

import java.util.Map;

/** Definition of config in Queryerr */
public interface IConfig
{
    /** Load config for extension name */
    Map<String, Object> loadExtensionConfig(String name);

    /**
     * Save config for provided extension name
     * 
     * <pre>
     * NOTE! If storing pojos here annotations like jackons JsonProperty etc. won't
     * work since extensions have their own classloader and core won't see those.
     * So best practice is to use plain objects or have proper beans that jackson can serialize without any annotations
     * </pre>
     */
    void saveExtensionConfig(String name, Map<String, Object> config);
}
