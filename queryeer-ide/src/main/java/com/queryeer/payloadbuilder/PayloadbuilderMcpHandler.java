package com.queryeer.payloadbuilder;

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.queryeer.api.extensions.engine.IMcpHandler;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension;
import com.queryeer.api.extensions.payloadbuilder.ICatalogExtension.ExceptionAction;
import com.queryeer.payloadbuilder.CatalogsConfigurable.QueryeerCatalog;
import com.queryeer.payloadbuilder.VariablesConfigurable.Environment;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryException;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.catalog.CatalogRegistry;
import se.kuseman.payloadbuilder.core.execution.QuerySession;

/** MCP handler for PLB. */
class PayloadbuilderMcpHandler implements IMcpHandler
{
    private final PayloadbuilderQueryEngine queryEngine;

    PayloadbuilderMcpHandler(PayloadbuilderQueryEngine queryEngine)
    {
        this.queryEngine = queryEngine;
    }

    @Override
    public String getParameterSyntaxHint()
    {
        return "Use @paramName syntax to safely inject parameter values (Payloadbuilder @-notation).";
    }

    @Override
    public Component getMcpConnectionComponent(Map<String, Object> config, Consumer<Boolean> dirtyConsumer)
    {
        List<Environment> environments = queryEngine.variablesConfigurable.getEnvironments();
        if (environments.isEmpty())
        {
            return null;
        }

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        panel.add(new JLabel("Environment:"));

        // First entry is blank (no environment)
        Environment[] items = new Environment[environments.size() + 1];
        items[0] = new Environment();
        for (int i = 0; i < environments.size(); i++)
        {
            items[i + 1] = environments.get(i);
        }
        JComboBox<Environment> combo = new JComboBox<>(items);

        // Pre-select based on stored name
        String storedName = (String) config.get("environment");
        if (storedName != null)
        {
            for (int i = 1; i < items.length; i++)
            {
                if (items[i].name.equals(storedName))
                {
                    combo.setSelectedIndex(i);
                    break;
                }
            }
        }

        combo.addActionListener(e ->
        {
            Environment selected = (Environment) combo.getSelectedItem();
            if (selected == null
                    || selected.name.isBlank())
            {
                config.remove("environment");
            }
            else
            {
                config.put("environment", selected.name);
            }
            dirtyConsumer.accept(true);
        });

        panel.add(combo);
        return panel;
    }

    @Override
    public void execute(Map<String, Object> mcpConnectionConfig, String query, Map<String, Object> parameters, OutputWriter outputWriter) throws Exception
    {
        QuerySession session = new QuerySession(new CatalogRegistry(), parameters);
        queryEngine.initCatalogs(session);

        String envName = (String) mcpConnectionConfig.get("environment");
        if (envName != null
                && !envName.isBlank())
        {
            Environment env = queryEngine.variablesConfigurable.getEnvironments()
                    .stream()
                    .filter(e -> e.name.equals(envName))
                    .findFirst()
                    .orElse(null);
            if (env != null)
            {
                queryEngine.variablesConfigurable.beforeQuery(session, env);
            }
        }

        int reRunCountLatch = 3;
        boolean complete = false;
        while (!complete)
        {
            try
            {
                CompiledQuery compiledQuery = Payloadbuilder.compile(session, query);
                QueryResult queryResult = compiledQuery.execute(session);

                while (queryResult.hasMoreResults())
                {
                    queryResult.writeResult(outputWriter);
                    // Flush after each result set
                    outputWriter.flush();
                }
                complete = true;
            }
            catch (CatalogException e)
            {
                // Let catalog extension handle exception
                Optional<ICatalogExtension> catalogExtension = queryEngine.catalogsConfigurable.getCatalogs()
                        .stream()
                        .filter(c -> Objects.equals(e.getCatalogAlias(), c.getAlias()))
                        .map(QueryeerCatalog::getCatalogExtension)
                        .findFirst();

                if (catalogExtension.isPresent()
                        && catalogExtension.get()
                                .handleException(session, e) == ExceptionAction.RERUN)
                {
                    reRunCountLatch--;
                    if (reRunCountLatch <= 0)
                    {
                        throw new QueryException("Query re reexecution limit was reached");
                    }

                    // Re-run query
                    continue;
                }

                throw e;
            }
        }
    }
}
