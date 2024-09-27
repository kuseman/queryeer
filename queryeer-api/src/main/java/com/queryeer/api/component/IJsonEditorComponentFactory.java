package com.queryeer.api.component;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.function.Function;

import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.extensions.IExtension;

/**
 * Definition of a factory that creates a editor that edits JSON. Supports for pretty, verify etc.
 */
public interface IJsonEditorComponentFactory extends IExtension
{
    /** Create a JSON editor component */
    <T> IJsonEditorComponent<T> create(Class<T> typeClazz, Function<T, String> verifier);

    /** Extension of {@link IConfigurable} for JSON editor that returns the resulting value */
    interface IJsonEditorComponent<T>
    {
        /** Json property. Fired when the edited json changes */
        public static final String CONTENT = "content";

        /** Return the component */
        Component getComponent();

        /** Return the result from the editor */
        T getResult();

        /** Load json file into editor */
        void load(File file);

        void save(File file);

        // /** Return result as json */
        // String getJsonResult();
        //
        // /** Set json value to editor */
        // void setJsonValue(String value);

        /** Add property change listener */
        void addPropertyChangeListener(PropertyChangeListener listener);
    }

    // /** Config of json configurable */
    // public static class FactoryConfig<T>
    // {
    //// /** Title of configurable */
    //// private final String title;
    //// /** Group name of configurable */
    //// private final String groupName;
    //// /** Config filename to read json from */
    //// private final String filename;
    //// /** Type class of underlying json */
    // private final Class<T> typeClazz;
    // private Object sampleObject;
    //
    // private Function<T, String> verifier;
    //
    // public FactoryConfig(String title, String groupName, String filename, Class<T> typeClazz)
    // {
    // this.title = requireNonNull(title, "title");
    // this.groupName = requireNonNull(groupName, "groupName");
    // this.filename = requireNonNull(filename, "filename");
    // this.typeClazz = requireNonNull(typeClazz, "typeClazz");
    // }
    //
    // public String getTitle()
    // {
    // return title;
    // }
    //
    // public String getGroupName()
    // {
    // return groupName;
    // }
    //
    // public String getFilename()
    // {
    // return filename;
    // }
    //
    // public Class<T> getTypeClazz()
    // {
    // return typeClazz;
    // }
    //
    // public Object getSampleObject()
    // {
    // return sampleObject;
    // }
    //
    // /** Set a sample object that will be serialized as json and shown when no config is present on disk */
    // public void setSampleObject(Object sampleObject)
    // {
    // this.sampleObject = sampleObject;
    // }
    //
    // public Function<T, String> getVerifier()
    // {
    // return verifier;
    // }
    //
    // /** Set a verifier function that verifies current editor value. */
    // public void setVerifier(Function<T, String> verifier)
    // {
    // this.verifier = verifier;
    // }
    // }
}
