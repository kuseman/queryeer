package com.queryeer;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.swing.event.SwingPropertyChangeSupport;

/** Queryeer model */
class QueryeerModel
{
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);
    public static final String FILES = "files";
    public static final String SELECTED_FILE = "selectedFile";

    private QueryFileModel selectedFile;
    private final List<QueryFileModel> files = new ArrayList<>();

    void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    void addFile(QueryFileModel file)
    {
        int index = files.size();
        files.add(file);

        pcs.fireIndexedPropertyChange(FILES, index, null, file);

        setSelectedFile(file);
    }

    void removeFile(QueryFileModel file)
    {
        int index = files.indexOf(file);
        if (index == -1)
        {
            return;
        }

        file.dispose();

        files.remove(index);

        pcs.fireIndexedPropertyChange(FILES, index, file, null);

        QueryFileModel selectedFile;
        // Select file at old position
        if (files.size() > index)
        {
            selectedFile = files.get(index);
        }
        else
        {
            selectedFile = null;
        }

        setSelectedFile(selectedFile);
    }

    void setSelectedFile(QueryFileModel file)
    {
        if (!Objects.equals(selectedFile, file))
        {
            QueryFileModel old = selectedFile;
            selectedFile = file;
            pcs.firePropertyChange(SELECTED_FILE, old, selectedFile);
        }
    }

    QueryFileModel getSelectedFile()
    {
        return selectedFile;
    }

    List<QueryFileModel> getFiles()
    {
        return files;
    }

    /** Tries to select provided file if in model. Returns true if found */
    boolean select(String file)
    {
        for (QueryFileModel model : files)
        {
            if (model.getFile()
                    .getAbsolutePath()
                    .equalsIgnoreCase(file))
            {
                setSelectedFile(model);
                return true;
            }
        }
        return false;
    }

    void close()
    {
        for (QueryFileModel file : files)
        {
            file.dispose();
        }

        try
        {
            QueryFileModel.DISPOSE_EXECUTOR.shutdown();
            QueryFileModel.DISPOSE_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
        }
    }
}
