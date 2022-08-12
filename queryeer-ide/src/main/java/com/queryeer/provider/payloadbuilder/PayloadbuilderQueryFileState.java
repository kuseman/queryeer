package com.queryeer.provider.payloadbuilder;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isAllBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.event.SwingPropertyChangeSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.queryeer.Constants;
import com.queryeer.api.IQueryFile;
import com.queryeer.api.IQueryFileState;
import com.queryeer.api.component.IQueryEditorComponent;
import com.queryeer.api.component.ISyntaxTextEditor;
import com.queryeer.api.extensions.IExtensionAction;
import com.queryeer.api.extensions.IMainToolbarAction;
import com.queryeer.api.extensions.IQueryProvider;
import com.queryeer.api.extensions.catalog.IPayloadbuilderQueryFileState;
import com.queryeer.api.service.IQueryFileProvider;

import se.kuseman.payloadbuilder.api.session.IQuerySession;
import se.kuseman.payloadbuilder.core.QuerySession;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;

/** State of a payloadbuilder query file */
class PayloadbuilderQueryFileState implements IPayloadbuilderQueryFileState
{
    private final SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);
    private final Map<String, Object> variables = new HashMap<>();
    private final QuerySession querySession = new QuerySession(new CatalogRegistry(), variables);
    private final List<CatalogModel> catalogs;
    private final ISyntaxTextEditor textEditor;
    private final IQueryEditorComponent editorComponent = new QueryEditorComponent();
    private final TextEditorModleChangeListener textModelChangeListener = new TextEditorModleChangeListener();
    private final VariablesDialog variablesDialog = new VariablesDialog();
    private IQueryFileProvider queryFileProvider;
    private IQueryProvider queryProvider;

    PayloadbuilderQueryFileState(IQueryProvider queryProvider, IQueryFileProvider queryFileProvider, ISyntaxTextEditor textEditor, List<CatalogModel> catalogs)
    {
        this.queryProvider = requireNonNull(queryProvider, "queryProvider");
        this.queryFileProvider = requireNonNull(queryFileProvider, "queryFileProvider");
        this.textEditor = requireNonNull(textEditor, "textEditor");
        this.catalogs = requireNonNull(catalogs, "catalogs");
        this.textEditor.getModel()
                .addPropertyChangeListener(textModelChangeListener);
    }

    @Override
    public IQueryProvider getQueryProvider()
    {
        return queryProvider;
    }

    @Override
    public void init(IQueryFile file)
    {
        querySession.setPrintWriter(file.getMessagesWriter());
        for (CatalogModel catalog : catalogs)
        {
            if (catalog.isDisabled())
            {
                continue;
            }
            // Register catalogs
            querySession.getCatalogRegistry()
                    .registerCatalog(catalog.getAlias(), catalog.getCatalogExtension()
                            .getCatalog());

            // Set first extension as default
            // We pick the first catalog that has a UI component
            if (isAllBlank(querySession.getDefaultCatalogAlias())
                    && !catalog.isDisabled()
                    && catalog.getCatalogExtension()
                            .getConfigurableClass() != null)
            {
                querySession.setDefaultCatalogAlias(catalog.getAlias());
            }
        }
    }

    @Override
    public void load(String filename) throws Exception
    {
        String string = FileUtils.readFileToString(new File(filename), StandardCharsets.UTF_8);

        textEditor.getModel()
                .setText(string);
        textEditor.getModel()
                .resetBuffer();
    }

    @Override
    public void save(String filename) throws IOException
    {
        FileUtils.write(new File(filename), textEditor.getModel()
                .getText(), StandardCharsets.UTF_8);
        textEditor.getModel()
                .resetBuffer();
    }

    @Override
    public IQueryEditorComponent getQueryEditorComponent()
    {
        return editorComponent;
    }

    @Override
    public void close()
    {
        textEditor.getModel()
                .removePropertyChangeListener(textModelChangeListener);
        textEditor.close();
    }

    @Override
    public IQuerySession getSession()
    {
        return querySession;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        pcs.removePropertyChangeListener(listener);
    }

    List<CatalogModel> getCatalogs()
    {
        return catalogs;
    }

    ISyntaxTextEditor getTextEditor()
    {
        return textEditor;
    }

    private class TextEditorModleChangeListener implements PropertyChangeListener
    {
        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (ISyntaxTextEditor.TextEditorModel.DIRTY.equals(evt.getPropertyName()))
            {
                // Propagate dirty to state listeners
                pcs.firePropertyChange(IQueryFileState.DIRTY, (boolean) evt.getOldValue(), (boolean) evt.getNewValue());
            }
        }

    }

    private class QueryEditorComponent implements IQueryEditorComponent
    {
        private List<IExtensionAction> actions;

        QueryEditorComponent()
        {

        }

        @Override
        public Component getComponent()
        {
            return textEditor.getComponent();
        }

        @Override
        public List<IExtensionAction> getComponentActions()
        {
            if (actions == null)
            {
                List<IExtensionAction> list = new ArrayList<>();
                list.addAll(textEditor.getActions());
                list.add(IMainToolbarAction.toolbarAction(11000, new AbstractAction("Edit variables", Constants.EDIT)
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        IQueryFile queryFile = queryFileProvider.getCurrentFile();

                        if (queryFile == null)
                        {
                            return;
                        }

                        Optional<PayloadbuilderQueryFileState> state = queryFile.getQueryFileState(PayloadbuilderQueryFileState.class);

                        if (!state.isPresent())
                        {
                            return;
                        }

                        String queryString = state.get()
                                .getTextEditor()
                                .getModel()
                                .getText();
                        if (isBlank(queryString))
                        {
                            return;
                        }

                        Set<String> variableNames = VariablesDialog.getVariables(queryString);

                        for (String name : variableNames)
                        {
                            if (!state.get().variables.containsKey(name))
                            {
                                state.get().variables.put(name, null);
                            }
                        }

                        variablesDialog.init(FilenameUtils.getName(queryFile.getFilename()), state.get().variables);
                        variablesDialog.setVisible(true);

                        Map<String, Object> variables = variablesDialog.getVariables();
                        if (variables != null)
                        {
                            state.get().variables.clear();
                            state.get().variables.putAll(variables);
                        }

                    }
                }));
                actions = unmodifiableList(list);
            }
            return actions;
        }
    }
}
