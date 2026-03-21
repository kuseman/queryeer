package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.queryeer.api.editor.ITextEditorDocumentParser;
import com.queryeer.api.extensions.engine.IQueryEngine;
import com.queryeer.api.extensions.engine.IQueryEngine.IState;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** JDBC engine state holding connection and query plan configuration for a query file. */
class JdbcEngineState implements IJdbcEngineState
{
    private final IQueryEngine queryEngine;

    private List<Runnable> changeListeners;
    final TextEditorDocumentParserProxy documentParser;
    ConnectionContext connectionContext;

    boolean includeQueryPlan;
    boolean estimateQueryPlan;

    JdbcEngineState(IQueryEngine queeryEngine)
    {
        this(queeryEngine, null);
    }

    JdbcEngineState(IQueryEngine queryEngine, ConnectionContext connectionContext)
    {
        this.queryEngine = requireNonNull(queryEngine);
        this.connectionContext = connectionContext;
        this.documentParser = new TextEditorDocumentParserProxy();

        resetParser();
    }

    IState cloneState()
    {
        JdbcEngineState newState = new JdbcEngineState(queryEngine);
        if (connectionContext != null)
        {
            newState.setConnectionContext(connectionContext.cloneState());
        }
        return newState;
    }

    void setConnectionContext(ConnectionContext connectionContext)
    {
        this.connectionContext = connectionContext;
        documentParser.currentParser = null;
        resetParser();

        if (changeListeners != null)
        {
            for (Runnable r : new ArrayList<>(changeListeners))
            {
                r.run();
            }
        }
    }

    void resetParser()
    {
        // Switch parser if we switched state
        if (connectionContext != null)
        {
            documentParser.currentParser = connectionContext.getJdbcDialect()
                    .getParser(this);

        }
    }

    @Override
    public void addChangeListener(Runnable r)
    {
        if (changeListeners == null)
        {
            changeListeners = new ArrayList<>();
        }
        changeListeners.add(r);
    }

    @Override
    public void removeChangeListener(Runnable r)
    {
        if (changeListeners == null)
        {
            return;
        }
        changeListeners.remove(r);
    }

    @Override
    public boolean isIncludeQueryPlan()
    {
        return includeQueryPlan;
    }

    @Override
    public boolean isEstimateQueryPlan()
    {
        return estimateQueryPlan;
    }

    // IState

    @Override
    public IQueryEngine getQueryEngine()
    {
        return queryEngine;
    }

    @Override
    public List<MetaParameter> getMetaParameters(boolean testData)
    {
        String url = "jdbc://server/database";
        String database = "database";
        if (!testData)
        {
            url = connectionContext != null ? Objects.toString(connectionContext.getJdbcConnection()
                    .getJdbcURL(), "")
                    : "";
            database = connectionContext != null ? connectionContext.getDatabase()
                    : "";
        }

        return IJdbcEngineState.getMetaParameters(url, database);
    }

    @Override
    public void close() throws IOException
    {
        if (connectionContext != null)
        {
            connectionContext.close();
        }
    }

    // IState

    @Override
    public String getDatabase()
    {
        if (connectionContext != null)
        {
            return connectionContext.getDatabase();
        }
        return null;
    }

    @Override
    public JdbcDialect getJdbcDialect()
    {
        if (connectionContext != null)
        {
            return connectionContext.getJdbcDialect();
        }
        return null;
    }

    @Override
    public JdbcConnection getJdbcConnection()
    {
        if (connectionContext != null)
        {
            return connectionContext.getJdbcConnection();
        }
        return null;
    }

    @Override
    public Connection createConnection() throws SQLException
    {
        if (connectionContext != null)
        {
            return connectionContext.createConnection();
        }
        return null;
    }

    /**
     * A dynamic proxy for {@link ITextEditorDocumentParser} that forwards all calls to a swappable {@link #currentParser}.
     *
     * <p>
     * Using {@link Proxy} instead of a manual per-method delegation means no code changes are needed here when new methods are added to {@link ITextEditorDocumentParser}: the proxy automatically
     * delegates them to {@link #currentParser} (or falls back to the interface default when no parser is set).
     * </p>
     *
     * <p>
     * {@code supports*()} methods always return {@code true} so the editor sets up all features (auto-complete, tooltips, signature hints…) even before a connection is established. Individual
     * dialects that don't implement a feature will simply return {@code null}/empty for the corresponding query methods.
     * </p>
     */
    static class TextEditorDocumentParserProxy implements InvocationHandler
    {
        ITextEditorDocumentParser currentParser;
        private final ITextEditorDocumentParser proxy;

        TextEditorDocumentParserProxy()
        {
            this.proxy = (ITextEditorDocumentParser) Proxy.newProxyInstance(ITextEditorDocumentParser.class.getClassLoader(), new Class<?>[] { ITextEditorDocumentParser.class }, this);
        }

        ITextEditorDocumentParser getParser()
        {
            return proxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            // Object methods (equals, hashCode, toString) are handled on the handler itself
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(this, args);
            }

            // supports*() always true: ensures editor infrastructure is configured at kit-install
            // time even before a connection (and therefore a real parser) exists
            if (method.getName()
                    .startsWith("supports"))
            {
                return true;
            }

            // All other interface methods: delegate to the real parser when available
            if (currentParser != null)
            {
                try
                {
                    return method.invoke(currentParser, args);
                }
                catch (InvocationTargetException e)
                {
                    throw e.getCause();
                }
            }

            // No parser yet — invoke the interface default (returns null / empty collection etc.)
            if (method.isDefault())
            {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            return null;
        }
    }
}
