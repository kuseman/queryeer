package com.queryeer.api.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.queryeer.api.extensions.engine.IQueryEngine;

/** Definition of config in Queryeer */
public interface IConfig
{
    /**
     * Load config for extension name. If file does not exits an empty map is returned
     */
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
    void saveExtensionConfig(String name, Map<String, ?> config);

    /** Return the full file for config file with provided extension name. */
    File getConfigFileName(String name);

    /**
     * Return list of available query engines. NOTE! Query engines are initalized lazy after plugin is wired so this method will return and empty list if called during wiring (in constructors)
     */
    List<IQueryEngine> getQueryEngines();
}
