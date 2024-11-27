package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JLabel;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.queryeer.api.event.ExecuteQueryEvent.OutputType;
import com.queryeer.api.extensions.IConfigurable;
import com.queryeer.api.service.IConfig;

import se.kuseman.payloadbuilder.catalog.jdbc.Constants;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectType;

/**
 * Configurable for query actions
 * 
 * <pre>
 * - Link actions (CTRL-hover/click) in connection tree or in text editor
 * - Context menu (right click) in connection tree
 * </pre>
 */
class QueryActionsConfigurable implements IConfigurable
{
    private static final String NAME = "com.queryeer.jdbc.QueryActions";
    private static final String NAME_DEFAULT = "com.queryeer.jdbc.QueryActions.default";
    private final IConfig config;
    /** Config with {@link JdbcDatabase} name as key and it's query actions as value */
    private Map<String, QueryActions> actionConfig;

    QueryActionsConfigurable(IConfig config)
    {
        this.config = requireNonNull(config, "config");
        load();
    }

    @Override
    public String getTitle()
    {
        return "Query Actions";
    }

    @Override
    public String groupName()
    {
        return Constants.TITLE;
    }

    @Override
    public Component getComponent()
    {
        JLabel label = new JLabel("Query Actions - UI comming soon, edit config manually");
        label.setAlignmentX(0.0f);
        return label;
    }

    @Override
    public void addDirtyStateConsumer(Consumer<Boolean> consumer)
    {
    }

    @Override
    public void removeDirtyStateConsumer(Consumer<Boolean> consumer)
    {
    }

    /** Return query actions for provided database type */
    List<QueryAction> getQueryActions(JdbcDatabase database, ActionTarget target)
    {
        if (actionConfig == null)
        {
            return emptyList();
        }
        QueryActions queryActions = actionConfig.get(database.name());
        if (queryActions == null)
        {
            return emptyList();
        }
        return queryActions.actions.stream()
                .filter(QueryAction::isValid)
                .filter(a -> a.target == target)
                .toList();
    }

    /*
     * @formatter:off
     * - database type
     * - object type
     * - action type
     * -- link
     * -- context
     * 
     * "databases": {
     *   "oracle": [
     *     {
     *       "name": "Top 500"
     *       "actionType": [ "LINK" ],
     *       "objectType": [ "TABLESOURCE" ],
     *       "query": [
     *         "",
     *         "",
     *         ""
     *       ]
     *     }
     *   ],
     *   "sqlserver": [
     *   
     *   ]
     * 
     * }
     * @formatter:on
     */

    private static final TypeReference<Map<String, QueryActions>> TYPE_REFERENCE = new TypeReference<Map<String, QueryActions>>()
    {
    };

    private void load()
    {
        File file = config.getConfigFileName(NAME);
        File fileDefault = config.getConfigFileName(NAME_DEFAULT);

        // Always copy the latest built in config to default so the user have something to create a custom config from
        String builtInConfig = getBuiltInConfig();
        writeConfig(fileDefault, builtInConfig);

        // Load config file
        if (file.exists())
        {
            try
            {
                actionConfig = Constants.MAPPER.readValue(file, TYPE_REFERENCE);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error loading config from: " + file.getAbsolutePath(), e);
            }
        }
        // .. else use the built in config
        else
        {
            try
            {
                actionConfig = Constants.MAPPER.readValue(builtInConfig, TYPE_REFERENCE);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error loading built in config", e);
            }
        }

        // else if (!fileDefault.exists())
        // {
        // String builtInConfig = getBuiltInConfig();
        // String defaultConfig = readDefaultConfig(fileDefault);
        //
        // if (!Objects.equals(builtInConfig, defaultConfig))
        // {
        // writeConfig(fileDefault, builtInConfig);
        // defaultConfig = builtInConfig;
        // }
        //
////            //@formatter:off
////            actionConfig = Map.of(
////                    SqlServerDatabase.NAME, createDefaultSqlServerActions(),
////                    OracleDatabase.NAME, createDefaultOracleActions(),
////                    BaseDatabase.NAME, createDefaultJdbcActions()
////            );
////            //@formatter:on
        //
        // try
        // {
        // Constants.MAPPER.writerWithDefaultPrettyPrinter()
        // .writeValue(file, actionConfig);
        // }
        // catch (IOException e)
        // {
        // throw new RuntimeException("Error saving config", e);
        // }
        // }
        // else
        // {

        init();
        // }
    }

    private String getBuiltInConfig()
    {
        try
        {
            return IOUtils.toString(QueryActionsConfigurable.class.getResourceAsStream("/se/kuseman/payloadbuilder/catalog/jdbc/com.queryeer.jdbc.QueryActions.cfg"), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return "";
        }
    }

    private void writeConfig(File file, String content)
    {
        try
        {
            FileUtils.write(file, content, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void init()
    {
        actionConfig.values()
                .forEach(qa ->
                {
                    Map<String, Query> map = qa.queries.stream()
                            .filter(q -> !StringUtils.isBlank(q.name))
                            .collect(toMap(q -> StringUtils.lowerCase(q.name), Function.identity()));

                    List<QueryAction> queue = new ArrayList<>(qa.actions);
                    while (!queue.isEmpty())
                    {
                        QueryAction a = queue.remove(0);
                        a.query = map.get(StringUtils.lowerCase(a.queryName));
                        queue.addAll(a.subItems);
                    }
                });
    }

    // static class QueryActionsConfig
    // {
    // @JsonProperty
    // private Map<String, QueryActionDatabase> databases = emptyMap();
    // }

    static class QueryActions
    {
        @JsonProperty
        List<Query> queries = emptyList();

        @JsonProperty
        List<QueryAction> actions = emptyList();

        public List<Query> getQueries()
        {
            return queries;
        }

        public void setQueries(List<Query> queries)
        {
            this.queries = queries;
        }

        public List<QueryAction> getActions()
        {
            return actions;
        }

        public void setActions(List<QueryAction> actions)
        {
            this.actions = actions;
        }
    }

    /** A query definition */
    static class Query
    {
        @JsonProperty
        private String name = "";
        @JsonProperty
        private Object query = "";

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getQuery()
        {
            if (query == null)
            {
                return "";
            }
            if (query instanceof String)
            {
                return (String) query;
            }
            else if (query instanceof Collection)
            {
                return ((Collection<?>) query).stream()
                        .map(Object::toString)
                        .collect(joining(" "));
            }
            return String.valueOf(query);
        }

        public void setQuery(Object query)
        {
            if (!(query instanceof Collection))
            {
                this.query = new ArrayList<>(asList(String.valueOf(query)
                        .split(System.lineSeparator())));
            }
            else
            {
                this.query = query;
            }
        }

    }

    /** A query action. Connection between a {@link Query} and where is should be applied */
    static class QueryAction
    {
        @JsonProperty
        String title = "";

        @JsonIgnore
        Query query;

        @JsonProperty
        String queryName;

        @JsonProperty
        private ActionType type;

        @JsonProperty
        private Set<ObjectType> objectTypes = emptySet();

        @JsonProperty
        private OutputType output = OutputType.TABLE;

        @JsonProperty
        private ActionTarget target;

        @JsonProperty
        private boolean disabled = false;

        /** Sub items. If set then this item acts as parent container only with no action */
        @JsonProperty
        private List<QueryAction> subItems = emptyList();

        public String getTitle()
        {
            if (!isBlank(title))
            {
                return title;
            }
            else if (query == null)
            {
                return null;
            }

            return query.name;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public String getQuery()
        {
            if (query == null)
            {
                return null;
            }

            return query.getQuery();
        }

        public ActionType getType()
        {
            return type;
        }

        public void setType(ActionType types)
        {
            this.type = types;
        }

        public Set<ObjectType> getObjectTypes()
        {
            return objectTypes;
        }

        public void setObject(Set<ObjectType> objectTypes)
        {
            this.objectTypes = objectTypes;
        }

        public OutputType getOutput()
        {
            return output;
        }

        public void setOutputType(OutputType output)
        {
            this.output = output;
        }

        public ActionTarget getTarget()
        {
            return target;
        }

        public void setTarget(ActionTarget target)
        {
            this.target = target;
        }

        public String getQueryName()
        {
            return queryName;
        }

        public void setQueryName(String queryName)
        {
            this.queryName = queryName;
        }

        public List<QueryAction> getSubItems()
        {
            return subItems;
        }

        public void setSubItems(List<QueryAction> subItems)
        {
            this.subItems = subItems;
        }

        public boolean isDisabled()
        {
            return disabled;
        }

        public void setDisabled(boolean disabled)
        {
            this.disabled = disabled;
        }

        private boolean isValid()
        {
            if (!subItems.isEmpty())
            {
                return type != null
                        && !CollectionUtils.isEmpty(objectTypes)
                        && target != null
                        && !disabled;
            }

            return query != null
                    && type != null
                    && !CollectionUtils.isEmpty(objectTypes)
                    && output != null
                    && target != null
                    && !disabled;
        }

        boolean containsTableSource()
        {
            for (ObjectType type : objectTypes)
            {
                if (type.isTableSource())
                {
                    return true;
                }
            }
            return false;
        }

        boolean containsUserDefinedFunctionProcedure()
        {
            for (ObjectType type : objectTypes)
            {
                if (type.isUserDefinedFunctionProcedure())
                {
                    return true;
                }
            }
            return false;
        }
    }

    enum ActionTarget
    {
        /** Target of action is navigation tree */
        NAVIGATION_TREE,

        /** Target of action is objects in text editor */
        TEXT_EDITOR
    }

    enum ActionType
    {
        /** Action is shown when CTRL-hover on target {@link ObjectType} */
        LINK,
        /** Action is shown when right click on target {@link ObjectType} in tree */
        CONTEXT_MENU
    }

    // private static QueryActions createDefaultSqlServerActions()
    // {
    // Query top500 = new Query();
    // top500.name = "Top 500";
    // top500.query = "select top 500 * from %s";
    //
    // QueryAction top500Tree = new QueryAction();
    // top500Tree.queryName = "Top 500";
    // top500Tree.query = top500;
    // top500Tree.object = ObjectType.TABLESOURCE;
    // top500Tree.type = ActionType.LINK;
    // top500Tree.target = ActionTarget.NAVIGATION_TREE;
    //
    // QueryAction top500Editor = new QueryAction();
    // top500Editor.queryName = "Top 500";
    // top500Editor.query = top500;
    // top500Editor.object = ObjectType.TABLESOURCE;
    // top500Editor.type = ActionType.LINK;
    // top500Editor.target = ActionTarget.TEXT_EDITOR;
    //
    // Query describe = new Query();
    // top500.name = "Describe";
    // top500.query = "exec sp_help '%s'";
    //
    // QueryAction describeTree = new QueryAction();
    // describeTree.queryName = "Describe";
    // describeTree.query = describe;
    // describeTree.object = ObjectType.TABLESOURCE;
    // describeTree.type = ActionType.LINK;
    // describeTree.target = ActionTarget.NAVIGATION_TREE;
    //
    // QueryAction describeEditor = new QueryAction();
    // describeEditor.queryName = "Describe";
    // describeEditor.query = describe;
    // describeEditor.object = ObjectType.TABLESOURCE;
    // describeEditor.type = ActionType.LINK;
    // describeEditor.target = ActionTarget.TEXT_EDITOR;
    //
    // QueryActions result = new QueryActions();
    // result.queries = List.of(top500, describe);
    // result.actions = List.of(top500Tree, describeTree, top500Editor, describeEditor);
    // return result;
    // }
    //
    // private static QueryActions createDefaultOracleActions()
    // {
    // Query top500 = new Query();
    // top500.name = "Top 500";
    // top500.query = "SELECT * FROM %s FETCH NEXT 500 ROWS ONLY";
    //
    // QueryAction top500Tree = new QueryAction();
    // top500Tree.queryName = "Top 500";
    // top500Tree.query = top500;
    // top500Tree.object = ObjectType.TABLESOURCE;
    // top500Tree.type = ActionType.LINK;
    // top500Tree.target = ActionTarget.NAVIGATION_TREE;
    //
    // QueryAction top500Editor = new QueryAction();
    // top500Editor.queryName = "Top 500";
    // top500Editor.query = top500;
    // top500Editor.object = ObjectType.TABLESOURCE;
    // top500Editor.type = ActionType.LINK;
    // top500Editor.target = ActionTarget.TEXT_EDITOR;
    //
    // QueryActions result = new QueryActions();
    // result.queries = List.of(top500);
    // result.actions = List.of(top500Tree, top500Editor);
    // return result;
    // }
    //
    // private static QueryActions createDefaultJdbcActions()
    // {
    // Query top500 = new Query();
    // top500.name = "Top 500";
    // // Might work for some for some not
    // top500.query = "select top 500 * from %s";
    //
    // QueryAction top500Tree = new QueryAction();
    // top500Tree.queryName = "Top 500";
    // top500Tree.query = top500;
    // top500Tree.object = ObjectType.TABLESOURCE;
    // top500Tree.type = ActionType.LINK;
    // top500Tree.target = ActionTarget.NAVIGATION_TREE;
    //
    // QueryAction top500Editor = new QueryAction();
    // top500Editor.queryName = "Top 500";
    // top500Editor.query = top500;
    // top500Editor.object = ObjectType.TABLESOURCE;
    // top500Editor.type = ActionType.LINK;
    // top500Editor.target = ActionTarget.TEXT_EDITOR;
    //
    // QueryActions result = new QueryActions();
    // result.queries = List.of(top500);
    // result.actions = List.of(top500Tree, top500Editor);
    // return result;
    // }
}
