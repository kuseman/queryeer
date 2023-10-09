package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import com.queryeer.api.extensions.output.queryplan.Node;
import com.queryeer.api.extensions.output.queryplan.Node.NodeProperty;

import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.AdaptiveJoinType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.AffectingConvertWarningType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.BaseStmtInfoType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ColumnGroupType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ColumnReferenceListType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ColumnReferenceType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ColumnType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ComputeScalarType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ConcatType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ConstantScanType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.DefinedValuesListType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.DefinedValuesListType.DefinedValue;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ExchangeSpillDetailsType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.FilterType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.HashSpillDetailsType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.HashType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.IndexKindType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.IndexScanType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.MemoryGrantWarningInfo;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.MergeType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.MissingIndexGroupType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.MissingIndexType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.MissingIndexesType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.NestedLoopsType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ObjectType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.OptimizerStatsUsageType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.OrderByType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ParallelismType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.QueryPlanType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.RelOpType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.RunTimeInformationType.RunTimeCountersPerThread;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ScalarExpressionListType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ScalarExpressionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ScalarType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ScanRangeType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.SeekPredicateType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.ShowPlanXML;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.SortSpillDetailsType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.SortType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.SpillOccurredType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.SpillToTempDbType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.StatsInfoType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.StmtCondType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.StmtSimpleType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.TableScanType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.TableValuedFunctionType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.TopSortType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.TopType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.WaitWarningType;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.sqlserver.showplan2019.WarningsType;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;

@SuppressWarnings("deprecation")
class SqlServerQueryPlanParser
{
    private static final String WARNINGS = "Warnings";
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlServerQueryPlanParser.class);
    private static final DecimalFormat TOOLTIP_DOUBLE_FORMAT = new DecimalFormat("##.########");

    /** Parse provided xml plan and return a query plan node structure. */
    static List<Node> parseXml(String planXml)
    {

        ClassLoader contextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        try
        {
            // JAXB is bundled inside this plugin and the thread that is executed is originating
            // from core
            Thread.currentThread()
                    .setContextClassLoader(ShowPlanXML.class.getClassLoader());
            JAXBContext jc = JAXBContext.newInstance(ShowPlanXML.class);
            Unmarshaller u = jc.createUnmarshaller();

            XMLReader xmlReader = SAXParserFactory.newInstance()
                    .newSAXParser()
                    .getXMLReader();

            NamespaceFilter inFilter = new NamespaceFilter("http://schemas.microsoft.com/sqlserver/2004/07/showplan", true);
            inFilter.setParent(xmlReader);

            InputSource is = new InputSource(new StringReader(planXml));
            SAXSource source = new SAXSource(inFilter, is);
            ShowPlanXML o = (ShowPlanXML) u.unmarshal(source);

            // TODO: try to investigate when there are more than one batch/statement etc.
            List<BaseStmtInfoType> stmInfos = o.getBatchSequence()
                    .getBatch()
                    .get(0)
                    .getStatements()
                    .get(0)
                    .getStmtSimpleOrStmtCondOrStmtCursor();

            return stmInfos.stream()
                    .map(stmInfo -> handle(new Context(), stmInfo, null))
                    .toList();
        }
        catch (Exception e)
        {
            LOGGER.error("Error unmarshalling SQLServer ShowPlanXML", e);
            return null;
        }
        finally
        {
            Thread.currentThread()
                    .setContextClassLoader(contextClassLoader);
        }
    }

    private static String formatValueForTooltip(Object value)
    {
        if (value instanceof Double d)
        {
            return TOOLTIP_DOUBLE_FORMAT.format(d);
        }
        return String.valueOf(value);
    }

    private static class Context
    {
        double rootEstimatedTotalSubtreeCost;
    }

    private static <T> Node handle(Context ctx, T o, Node parent)
    {
        if (o == null)
        {
            return parent;
        }

        @SuppressWarnings("unchecked")
        NodeHandler<T> function = (NodeHandler<T>) HANDLERS.get(o.getClass());
        if (function != null)
        {
            return function.handle(ctx, o, parent);
        }
        else
        {
            Node node = new Node("Missing%simplementation%sof%s%s".formatted(System.lineSeparator(), System.lineSeparator(), System.lineSeparator(), o.getClass()
                    .getSimpleName()), "", List.of());
            if (parent != null)
            {
                parent.children()
                        .add(new Node.NodeLink("", List.of(), node));
            }
            else
            {
                return node;
            }
            LOGGER.warn("No Query plan handler for class: {}", o.getClass());
        }
        return parent;
    }

    private static NodeHandler<FilterType> FilterType_Handler = (Context ctx, FilterType o, Node parent) ->
    {
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));
        parent.properties()
                .add(getScalarExpressionProperty("Predicate", o.getPredicate()));
        handle(ctx, o.getRelOp(), parent);
        return parent;
    };

    private static NodeHandler<SortType> SortType_Handler = (Context ctx, SortType o, Node parent) ->
    {
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));
        parent.properties()
                .add(new NodeProperty("Distinct", o.isDistinct()));

        NodeProperty orderByProp = new NodeProperty("Order By", null);
        parent.properties()
                .add(orderByProp);

        int index = 0;
        for (OrderByType.OrderByColumn type : o.getOrderBy()
                .getOrderByColumn())
        {
            NodeProperty prop = new NodeProperty("[" + index++ + "]", getColumnReferenceLabel(type.getColumnReference()));
            prop.subProperties()
                    .add(new NodeProperty("Ascending", type.isAscending()));
            addColumnReferenceSubProperties(prop, type.getColumnReference());

            orderByProp.subProperties()
                    .add(prop);
        }

        handle(ctx, o.getRelOp(), parent);
        return parent;
    };

    private static NodeHandler<ConstantScanType> ConstantScanType_Handler = (Context ctx, ConstantScanType o, Node parent) ->
    {
        if (o.getValues() != null)
        {
            NodeProperty values = new NodeProperty("Values", null);
            parent.properties()
                    .add(values);

            int index = 0;
            for (ScalarExpressionListType type : o.getValues()
                    .getRow())
            {
                NodeProperty prop = new NodeProperty("[" + index++ + "]", null);
                values.subProperties()
                        .add(prop);

                int subIndex = 0;
                for (ScalarType t : type.getScalarOperator())
                {
                    prop.subProperties()
                            .add(new NodeProperty("[" + subIndex++ + "]", t.getScalarString()));
                }
            }
        }

        return parent;
    };

    private static NodeHandler<TableValuedFunctionType> TableValuedFunctionType_Handler = (Context ctx, TableValuedFunctionType o, Node parent) ->
    {
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));
        parent.properties()
                .add(getObject(o.getObject()));
        if (o.getRelOp() != null)
        {
            handle(ctx, o.getRelOp(), parent);
        }

        return parent;
    };

    private static NodeHandler<ComputeScalarType> ComputeScalarType_Handler = (Context ctx, ComputeScalarType o, Node parent) ->
    {
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));
        parent.properties()
                .add(new NodeProperty("ComputeSequence", o.isComputeSequence()));
        Node node = handle(ctx, o.getRelOp(), parent).children()
                .get(0)
                .node();

        // Replace the parent row count with the child row count since compute scalar
        // doesn't have runtime information
        parent.properties()
                .removeIf(p -> NodeProperty.ROW_COUNT.equalsIgnoreCase(p.name()));

        NodeProperty rowCount = node.properties()
                .stream()
                .filter(p -> NodeProperty.ROW_COUNT.equalsIgnoreCase(p.name()))
                .findAny()
                .orElse(null);
        if (rowCount != null)
        {
            parent.properties()
                    .add(rowCount);
        }
        return parent;
    };

    private static NodeHandler<ConcatType> ConcatType_Handler = (Context ctx, ConcatType o, Node parent) ->
    {
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));

        for (RelOpType relOpType : o.getRelOp())
        {
            handle(ctx, relOpType, parent);
        }
        return parent;
    };

    private static NodeHandler<ParallelismType> ParallelismType_Handler = (Context ctx, ParallelismType o, Node parent) ->
    {
        parent.properties()
                .add(getColumnList("", "PartitionColumns", false, o.getPartitionColumns()));
        parent.properties()
                .add(getColumnList("", "HashKeys", false, o.getHashKeys()));
        parent.properties()
                .add(getScalarExpressionProperty("Predicate", o.getPredicate()));
        parent.properties()
                .add(new NodeProperty("PartitioningType", o.getPartitioningType()));
        parent.properties()
                .add(new NodeProperty("Remoting", o.isRemoting()));
        parent.properties()
                .add(new NodeProperty("LocalParallelism", o.isLocalParallelism()));
        parent.properties()
                .add(new NodeProperty("InRow", o.isInRow()));
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));

        handle(ctx, o.getRelOp(), parent);
        return parent;
    };

    private static NodeHandler<TableScanType> TableScanType_Handler = (Context ctx, TableScanType o, Node parent) ->
    {
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));
        parent.properties()
                .add(getScalarExpressionProperty("Predicate", o.getPredicate()));
        parent.properties()
                .add(new NodeProperty("Ordered", o.isOrdered()));
        parent.properties()
                .add(new NodeProperty("ForcedIndex", o.isForcedIndex()));
        parent.properties()
                .add(new NodeProperty("ForcedScan", o.isForceScan()));
        parent.properties()
                .add(new NodeProperty("NoExpandHint", o.isNoExpandHint()));
        parent.properties()
                .add(getObject(o.getObject()
                        .get(0)));

        return parent;
    };

    private static NodeHandler<IndexScanType> IndexScanType_Handler = (Context ctx, IndexScanType o, Node parent) ->
    {

        if (o.getPredicate() != null)
        {
            if (o.getPredicate()
                    .size() == 1)
            {
                parent.properties()
                        .add(getScalarExpressionProperty("Predicate", o.getPredicate()
                                .get(0)));
            }
            else
            {
                NodeProperty predicateProp = new NodeProperty("Predicate", null);
                parent.properties()
                        .add(predicateProp);

                int index = 0;
                for (ScalarExpressionType predicate : o.getPredicate())
                {
                    Node.NodeProperty prop = new Node.NodeProperty("[" + index++ + "]", null);
                    predicateProp.subProperties()
                            .add(prop);

                    prop.subProperties()
                            .add(getScalarExpressionProperty("Predicate", predicate));
                }
            }
        }

        if (o.getSeekPredicates() != null)
        {
            List<SeekPredicateType> seekPredicates = null;
            if (o.getSeekPredicates()
                    .getSeekPredicate() != null
                    && !o.getSeekPredicates()
                            .getSeekPredicate()
                            .isEmpty())
            {
                seekPredicates = o.getSeekPredicates()
                        .getSeekPredicate();
            }
            else if (o.getSeekPredicates()
                    .getSeekPredicateNew() != null
                    && !o.getSeekPredicates()
                            .getSeekPredicateNew()
                            .isEmpty())
            {
                seekPredicates = o.getSeekPredicates()
                        .getSeekPredicateNew()
                        .stream()
                        .flatMap(p -> p.getSeekKeys()
                                .stream())
                        .toList();
            }
            else if (o.getSeekPredicates()
                    .getSeekPredicatePart() != null
                    && !o.getSeekPredicates()
                            .getSeekPredicatePart()
                            .isEmpty())
            {
                seekPredicates = o.getSeekPredicates()
                        .getSeekPredicatePart()
                        .stream()
                        .flatMap(p -> p.getSeekPredicateNew()
                                .stream())
                        .flatMap(p -> p.getSeekKeys()
                                .stream())
                        .toList();
            }
            if (seekPredicates != null)
            {
                Node.NodeProperty prop = new Node.NodeProperty("SeekPredicates", null);
                parent.properties()
                        .add(prop);
                int index = 0;
                for (SeekPredicateType st : seekPredicates)
                {
                    Node.NodeProperty stProp = new Node.NodeProperty("[" + index++ + "]", null);
                    prop.subProperties()
                            .add(stProp);

                    if (st.getIsNotNull() != null)
                    {
                        Node.NodeProperty isNotNull = new NodeProperty("IsNotNull", getColumnReferenceLabel(st.getIsNotNull()
                                .getColumnReference()));
                        addColumnReferenceSubProperties(isNotNull, st.getIsNotNull()
                                .getColumnReference());
                        stProp.subProperties()
                                .add(isNotNull);
                    }
                    if (st.getPrefix() != null)
                    {
                        stProp.subProperties()
                                .add(getScanRangeTypeProperties("Prefix", st.getPrefix()));
                    }
                    if (st.getStartRange() != null)
                    {
                        stProp.subProperties()
                                .add(getScanRangeTypeProperties("Start", st.getStartRange()));
                    }
                    if (st.getEndRange() != null)
                    {
                        stProp.subProperties()
                                .add(getScanRangeTypeProperties("End", st.getEndRange()));
                    }
                }
            }
        }

        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));
        parent.properties()
                .add(new NodeProperty("Lookup", o.isLookup()));
        parent.properties()
                .add(new NodeProperty("Ordered", o.isOrdered()));
        parent.properties()
                .add(new NodeProperty("ScanDirection", o.getScanDirection()));
        parent.properties()
                .add(new NodeProperty("ForcedIndex", o.isForcedIndex()));
        parent.properties()
                .add(new NodeProperty("ForcedSeek", o.isForceSeek()));
        parent.properties()
                .add(new NodeProperty("ForcedScan", o.isForceScan()));
        parent.properties()
                .add(new NodeProperty("NoExpandHint", o.isNoExpandHint()));
        parent.properties()
                .add(new NodeProperty("DynamicSeek", o.isDynamicSeek()));
        parent.properties()
                .add(new NodeProperty("ForceSeekColumnCount", o.getForceSeekColumnCount()));
        parent.properties()
                .add(getObject(o.getObject()
                        .get(0)));
        return parent;
    };

    private static NodeProperty getScanRangeTypeProperties(String label, ScanRangeType rangeType)
    {
        Node.NodeProperty prop = new NodeProperty(label, null);
        prop.subProperties()
                .add(new NodeProperty("ScanType", rangeType.getScanType()));

        Node.NodeProperty rangeColumns = new NodeProperty("Rangecolumns", null);
        Node.NodeProperty rangeExpressions = new NodeProperty("RangeExpressions", null);
        prop.subProperties()
                .add(rangeColumns);
        prop.subProperties()
                .add(rangeExpressions);

        for (ColumnReferenceType col : rangeType.getRangeColumns()
                .getColumnReference())
        {
            NodeProperty colProp = new NodeProperty(getColumnReferenceLabel(col), null);
            addColumnReferenceSubProperties(colProp, col);
            rangeColumns.subProperties()
                    .add(colProp);
        }
        int index = 0;
        for (ScalarType scalarType : rangeType.getRangeExpressions()
                .getScalarOperator())
        {
            rangeExpressions.subProperties()
                    .add(new NodeProperty("[" + index++ + "]", scalarType.getScalarString()));
        }

        return prop;
    }

    private static NodeHandler<TopType> TopType_Handler = (Context ctx, TopType o, Node parent) ->
    {
        parent.properties()
                .add(new NodeProperty("Rows", o.getRows()));
        parent.properties()
                .add(new NodeProperty("Percentage", o.isIsPercent()));
        parent.properties()
                .add(getScalarExpressionProperty("TopExpression", o.getTopExpression()));
        parent.properties()
                .add(getScalarExpressionProperty("OffsetExpression", o.getOffsetExpression()));

        handle(ctx, o.getRelOp(), parent);
        return parent;
    };

    private static NodeHandler<MergeType> MergeType_Handler = (Context ctx, MergeType o, Node parent) ->
    {
        parent.properties()
                .add(getColumnList("", "InnerSideJoinColumns", false, o.getInnerSideJoinColumns()));
        parent.properties()
                .add(getColumnList("", "OuterSideJoinColumns", false, o.getOuterSideJoinColumns()));
        parent.properties()
                .add(new NodeProperty("ManyToMany", o.isManyToMany()));
        parent.properties()
                .add(getScalarExpressionProperty("Residual", o.getResidual()));
        parent.properties()
                .add(getScalarExpressionProperty("PassThru", o.getPassThru()));
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));

        for (RelOpType relOpType : o.getRelOp())
        {
            handle(ctx, relOpType, parent);
        }
        return parent;
    };

    private static NodeHandler<NestedLoopsType> NestedLoopsType_Handler = (Context ctx, NestedLoopsType o, Node parent) ->
    {
        parent.properties()
                .add(new NodeProperty("Optimized", o.isOptimized()));
        parent.properties()
                .add(new NodeProperty("WithOrderedPrefetch", o.isWithOrderedPrefetch()));
        parent.properties()
                .add(new NodeProperty("WithUnorderedPrefetch", o.isWithUnorderedPrefetch()));
        parent.properties()
                .add(getColumnList("", "OuterReferences", false, o.getOuterReferences()));
        parent.properties()
                .add(getScalarExpressionProperty("Predicate", o.getPredicate()));
        parent.properties()
                .add(getScalarExpressionProperty("PassThru", o.getPassThru()));
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));

        for (RelOpType relOpType : o.getRelOp())
        {
            handle(ctx, relOpType, parent);
        }
        return parent;
    };

    private static NodeHandler<HashType> HashType_Handler = (Context ctx, HashType o, Node parent) ->
    {
        parent.properties()
                .add(getColumnList("", "HashKeysBuild", false, o.getHashKeysBuild()));
        parent.properties()
                .add(getColumnList("", "HashKeysProbe", false, o.getHashKeysProbe()));
        parent.properties()
                .add(getScalarExpressionProperty("BuildResidual", o.getBuildResidual()));
        parent.properties()
                .add(getScalarExpressionProperty("ProbeResidual", o.getProbeResidual()));
        parent.properties()
                .add(new NodeProperty("BitmapCreator", o.isBitmapCreator()));
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));

        for (RelOpType relOpType : o.getRelOp())
        {
            handle(ctx, relOpType, parent);
        }
        return parent;
    };

    private static NodeHandler<AdaptiveJoinType> AdaptiveJoinType_Handler = (Context ctx, AdaptiveJoinType o, Node parent) ->
    {
        parent.properties()
                .add(getColumnList("", "HashKeysBuild", false, o.getHashKeysBuild()));
        parent.properties()
                .add(getColumnList("", "HashKeysProbe", false, o.getHashKeysProbe()));
        parent.properties()
                .add(getScalarExpressionProperty("BuildResidual", o.getBuildResidual()));
        parent.properties()
                .add(getScalarExpressionProperty("ProbeResidual", o.getProbeResidual()));
        parent.properties()
                .add(new NodeProperty("BitmapCreator", o.isBitmapCreator()));
        parent.properties()
                .add(new NodeProperty("Optimized", o.isOptimized()));
        parent.properties()
                .add(new NodeProperty("WithOrderedPrefetch", o.isWithOrderedPrefetch()));
        parent.properties()
                .add(new NodeProperty("WithUnorderedPrefetch", o.isWithUnorderedPrefetch()));
        parent.properties()
                .add(getColumnList("", "OuterReferences", false, o.getOuterReferences()));
        parent.properties()
                .add(getScalarExpressionProperty("Predicate", o.getPredicate()));
        parent.properties()
                .add(getScalarExpressionProperty("PassThru", o.getPassThru()));
        parent.properties()
                .add(getDefinedValues(o.getDefinedValues()));

        for (RelOpType relOpType : o.getRelOp())
        {
            handle(ctx, relOpType, parent);
        }
        return parent;
    };

    private static NodeHandler<RelOpType> RelOpType_Handler = (Context ctx, RelOpType o, Node parent) ->
    {
        String physicalOp = WordUtils.capitalizeFully(StringUtils.replaceChars(o.getPhysicalOp()
                .name(), '_', ' '));
        String logicalOp = WordUtils.capitalizeFully(StringUtils.replaceChars(o.getLogicalOp()
                .name(), '_', ' '));

        double operatorCost = o.getEstimateCPU() + o.getEstimateIO();
        String objectLabel = "";
        if (o.getTableScan() != null)
        {
            objectLabel = getObjectLabel(o.getTableScan()
                    .getObject()
                    .get(0), true);
            // For leafs we use the sub tree cost as operator cost
            operatorCost = o.getEstimatedTotalSubtreeCost();
        }
        else if (o.getIndexScan() != null)
        {
            objectLabel = getObjectLabel(o.getIndexScan()
                    .getObject()
                    .get(0), true);

            if (Objects.equals(Boolean.TRUE, o.getIndexScan()
                    .isLookup()))
            {
                logicalOp = "Key Lookup";
            }

            if (o.getIndexScan()
                    .getObject()
                    .get(0)
                    .getIndexKind() == IndexKindType.CLUSTERED)
            {
                logicalOp += " (Clustered)";
            }

            physicalOp = logicalOp;
            operatorCost = o.getEstimatedTotalSubtreeCost();
        }
        else if (o.getTableValuedFunction() != null)
        {
            objectLabel = getObjectLabel(o.getTableValuedFunction()
                    .getObject(), true);
            operatorCost = o.getEstimatedTotalSubtreeCost();
        }
        else if (o.getExtension() != null)
        {
            objectLabel = o.getExtension()
                    .getUDXName();
        }

        //@formatter:off
        String label = !StringUtils.equalsIgnoreCase(logicalOp, physicalOp) 
                ? "%s<br/>(%s)".formatted("<html><b>" + physicalOp + "</b>", logicalOp)
                : "%s%s".formatted("<html><b>" + logicalOp + "</b>", !isBlank(objectLabel) ? "<br/>" + objectLabel : "");
        //@formatter:on

        if (ctx.rootEstimatedTotalSubtreeCost != 0)
        {
            label += String.format("<br/>Cost: %.0f%%", 100 * (operatorCost / ctx.rootEstimatedTotalSubtreeCost));
        }
        WarningsType warnings = o.getWarnings();
        List<NodeProperty> nodeProperties = new ArrayList<>();

        if (warnings != null)
        {
            nodeProperties.add(getWarningsProperty(warnings));
            nodeProperties.add(new NodeProperty(NodeProperty.HAS_WARNINGS, true));
        }

        nodeProperties.add(new NodeProperty("Parallel", o.isParallel()));
        if (o.isParallel())
        {
            nodeProperties.add(new NodeProperty(NodeProperty.PARALLEL, true));
        }

        nodeProperties.add(new Node.NodeProperty("EstimateCPU", o.getEstimateCPU(), true));
        nodeProperties.add(new Node.NodeProperty("EstimateIO", o.getEstimateIO(), true));
        nodeProperties.add(new Node.NodeProperty("EstimateRebinds", o.getEstimateRebinds(), true));
        nodeProperties.add(new Node.NodeProperty("EstimateRewinds", o.getEstimateRewinds(), true));
        nodeProperties.add(new Node.NodeProperty("EstimatedExecutionMode", o.getEstimatedExecutionMode(), true));
        nodeProperties.add(new Node.NodeProperty("EstimateRows", o.getEstimateRows(), true));
        nodeProperties.add(new Node.NodeProperty("EstimateRowsWithoutRowGoal", o.getEstimateRowsWithoutRowGoal(), true));
        nodeProperties.add(new Node.NodeProperty("EstimatedTotalSubtreeCost", o.getEstimatedTotalSubtreeCost(), true));
        nodeProperties.add(new Node.NodeProperty("EstimatedRowsRead", o.getEstimatedRowsRead(), true));
        nodeProperties.add(new Node.NodeProperty("Node ID", o.getNodeId(), true));
        nodeProperties.add(new Node.NodeProperty("AdaptiveThresholdRows", o.getAdaptiveThresholdRows()));
        nodeProperties.add(getColumnList(null, "Output List", false, o.getOutputList()));
        if (ctx.rootEstimatedTotalSubtreeCost != 0)
        {
            nodeProperties.add(new Node.NodeProperty("EstimatedOperatorCost",
                    String.format("%s (%s)", formatValueForTooltip(operatorCost), String.format("%.0f%%", 100 * (operatorCost / ctx.rootEstimatedTotalSubtreeCost))), true));
        }

        // CSOFF
        double estimatedRowCount = o.getEstimateRows();
        // CSON
        Double actualRowCount = null;
        Double actualRowReadCount = null;
        Double actualLogicalReads = null;
        Double actualPhysicalReads = null;
        Integer actualExecutions = null;

        NodeProperty runtimeInformation = null;
        if (o.getRunTimeInformation() != null)
        {
            actualRowCount = 0d;
            actualRowReadCount = 0d;
            actualLogicalReads = 0d;
            actualPhysicalReads = 0d;
            actualExecutions = 0;

            runtimeInformation = new NodeProperty("RuntimeInformation", null);
            nodeProperties.add(runtimeInformation);
            for (RunTimeCountersPerThread t : o.getRunTimeInformation()
                    .getRunTimeCountersPerThread())
            {
                actualRowCount += t.getActualRows()
                        .doubleValue();
                actualRowReadCount += ObjectUtils.defaultIfNull(t.getActualRowsRead(), 0)
                        .doubleValue();
                actualLogicalReads += ObjectUtils.defaultIfNull(t.getActualLogicalReads(), 0)
                        .doubleValue();
                actualPhysicalReads += ObjectUtils.defaultIfNull(t.getActualPhysicalReads(), 0)
                        .doubleValue();
                actualExecutions += ObjectUtils.defaultIfNull(t.getActualExecutions(), 0)
                        .intValue();

                Node.NodeProperty prop = new Node.NodeProperty("Thread", t.getThread());

                prop.subProperties()
                        .add(new NodeProperty("ActualRebinds", t.getActualRebinds()));
                prop.subProperties()
                        .add(new NodeProperty("ActualRewinds", t.getActualRewinds()));
                prop.subProperties()
                        .add(new NodeProperty("ActualRows", t.getActualRows()));
                prop.subProperties()
                        .add(new NodeProperty("ActualRowsRead", t.getActualRowsRead()));
                prop.subProperties()
                        .add(new NodeProperty("Batches", t.getBatches()));
                prop.subProperties()
                        .add(new NodeProperty("ActualEndOfScans", t.getActualEndOfScans()));
                prop.subProperties()
                        .add(new NodeProperty("ActualExecutions", t.getActualExecutions()));
                prop.subProperties()
                        .add(new NodeProperty("ActualExecutionMode", t.getActualExecutionMode()));
                prop.subProperties()
                        .add(new NodeProperty("ActualElapsed ms", t.getActualElapsedms()));
                prop.subProperties()
                        .add(new NodeProperty("ActualCPU ms", t.getActualCPUms()));
                prop.subProperties()
                        .add(new NodeProperty("ActualScans", t.getActualScans()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLogicalReads", t.getActualLogicalReads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualPhysicalReads", t.getActualPhysicalReads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualPageServerReads", t.getActualPageServerReads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualReadAheads", t.getActualReadAheads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualPageServerReadAheads", t.getActualPageServerReadAheads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLobLogicalReads", t.getActualLobLogicalReads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLobPhysicalReads", t.getActualLobPhysicalReads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLobPageServerReads", t.getActualLobPageServerReads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLobReadAheads", t.getActualLobReadAheads()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLobPageServerReadAheads", t.getActualLobPageServerReadAheads()));
                prop.subProperties()
                        .add(new NodeProperty("SegmentReads", t.getSegmentReads()));
                prop.subProperties()
                        .add(new NodeProperty("SegmentSkips", t.getSegmentSkips()));
                prop.subProperties()
                        .add(new NodeProperty("ActualLocallyAggregatedRows", t.getActualLocallyAggregatedRows()));
                prop.subProperties()
                        .add(new NodeProperty("InputMemoryGrant", t.getInputMemoryGrant()));
                prop.subProperties()
                        .add(new NodeProperty("OutputMemoryGrant", t.getOutputMemoryGrant()));
                prop.subProperties()
                        .add(new NodeProperty("UsedMemoryGrant", t.getUsedMemoryGrant()));
                prop.subProperties()
                        .add(new NodeProperty("IsInterleavedExecuted", t.isIsInterleavedExecuted()));
                prop.subProperties()
                        .add(new NodeProperty("ActualJoinType", t.getActualJoinType()));

                if (o.getRunTimeInformation()
                        .getRunTimeCountersPerThread()
                        .size() == 1)
                {
                    runtimeInformation.subProperties()
                            .addAll(prop.subProperties());
                    break;
                }

                runtimeInformation.subProperties()
                        .add(prop);
            }
        }

        nodeProperties.add(new NodeProperty("ActualRows", actualRowCount, true));
        nodeProperties.add(new NodeProperty("ActualRowsRead", actualRowReadCount, true));
        nodeProperties.add(new NodeProperty("ActualLogicalReads", actualLogicalReads, true));
        nodeProperties.add(new NodeProperty("ActualPhysicalReads", actualPhysicalReads, true));
        nodeProperties.add(new NodeProperty("ActualExecutions", actualExecutions, true));
        nodeProperties.add(new Node.NodeProperty(Node.NodeProperty.ROW_COUNT, ObjectUtils.defaultIfNull(actualRowCount, estimatedRowCount)));

        List<NodeProperty> linkProperties = new ArrayList<>();
        linkProperties.add(new Node.NodeProperty("EstimatedRows", o.getEstimateRows(), true));
        if (actualRowCount != null)
        {
            linkProperties.add(new Node.NodeProperty("ActualRows", actualRowCount, true));
        }

        Node node = new Node(label, "", nodeProperties);

        parent.children()
                .add(new Node.NodeLink("", linkProperties, node));

        /*
         * <element name="ExternalSelect" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ExternalSelectType"/> <element name="ExtExtractScan"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RemoteType"/> <element name="Filter" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}FilterType"/> <element
         * name="ForeignKeyReferencesCheck" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ForeignKeyReferencesCheckType"/> <element name="GbAgg"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}GbAggType"/> <element name="GbApply" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}GbApplyType"/> <element
         * name="Generic" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}GenericType"/> <element name="Get" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}GetType"/>
         * <element name="Hash" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}HashType"/> <element name="IndexScan"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}IndexScanType"/> <element name="InsertedScan" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RowsetType"/>
         * <element name="Insert" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}DMLOpType"/> <element name="Join"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}JoinType"/> <element name="LocalCube" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}LocalCubeType"/> <element
         * name="LogRowScan" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RelOpBaseType"/> <element name="Merge"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}MergeType"/> <element name="MergeInterval"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SimpleIteratorOneChildType"/> <element name="Move" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}MoveType"/>
         * <element name="NestedLoops" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}NestedLoopsType"/> <element name="OnlineIndex"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}CreateIndexType"/> <element name="Parallelism"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ParallelismType"/> <element name="ParameterTableScan"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RelOpBaseType"/> <element name="PrintDataflow" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RelOpBaseType"/>
         * <element name="Project" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ProjectType"/> <element name="Put"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}PutType"/> <element name="RemoteFetch" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RemoteFetchType"/>
         * <element name="RemoteModify" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RemoteModifyType"/> <element name="RemoteQuery"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RemoteQueryType"/> <element name="RemoteRange"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RemoteRangeType"/> <element name="RemoteScan" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}RemoteType"/>
         * <element name="RowCountSpool" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SpoolType"/> <element name="ScalarInsert"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ScalarInsertType"/> <element name="Segment" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SegmentType"/>
         * <element name="Sequence" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SequenceType"/> <element name="SequenceProject"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ComputeScalarType"/> <element name="SimpleUpdate"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SimpleUpdateType"/> <element name="Sort" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SortType"/> <element
         * name="Split" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SplitType"/> <element name="Spool" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SpoolType"/>
         * <element name="StreamAggregate" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}StreamAggregateType"/> <element name="Switch"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}SwitchType"/> <element name="TableScan" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}TableScanType"/>
         * <element name="TableValuedFunction" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}TableValuedFunctionType"/> <element name="Top"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}TopType"/> <element name="TopSort" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}TopSortType"/> <element
         * name="Update" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}UpdateType"/> <element name="Union" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ConcatType"/>
         * <element name="UnionAll" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}ConcatType"/> <element name="WindowSpool"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}WindowType"/> <element name="WindowAggregate"
         * type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}WindowAggregateType"/> <element name="XcsScan" type="{http://schemas.microsoft.com/sqlserver/2004/07/showplan}XcsScanType"/>
         */

        if (o.getAdaptiveJoin() != null)
        {
            handle(ctx, o.getAdaptiveJoin(), node);
        }
        else if (o.getTop() != null)
        {
            handle(ctx, o.getTop(), node);
        }
        else if (o.getNestedLoops() != null)
        {
            handle(ctx, o.getNestedLoops(), node);
        }
        else if (o.getParallelism() != null)
        {
            handle(ctx, o.getParallelism(), node);
        }
        else if (o.getMerge() != null)
        {
            handle(ctx, o.getMerge(), node);
        }
        else if (o.getHash() != null)
        {
            handle(ctx, o.getHash(), node);
        }
        else if (o.getIndexScan() != null)
        {
            handle(ctx, o.getIndexScan(), node);
        }
        else if (o.getTableScan() != null)
        {
            handle(ctx, o.getTableScan(), node);
        }
        else if (o.getConcat() != null)
        {
            handle(ctx, o.getConcat(), node);
        }
        else if (o.getConstantScan() != null)
        {
            handle(ctx, o.getConstantScan(), node);
        }
        else if (o.getComputeScalar() != null)
        {
            handle(ctx, o.getComputeScalar(), node);
        }
        else if (o.getSequenceProject() != null)
        {
            handle(ctx, o.getSequenceProject(), node);
        }
        else if (o.getFilter() != null)
        {
            handle(ctx, o.getFilter(), node);
        }
        else if (o.getSort() != null)
        {
            handle(ctx, o.getSort(), node);
        }
        else if (o.getTopSort() != null)
        {
            handle(ctx, o.getTopSort(), node);
        }
        // TODO: add handlers for ops below, for now we just proceed to the child RelOpType(s)
        else if (o.getUpdate() != null)
        {
            handle(ctx, o.getUpdate()
                    .getRelOp(), node);
        }

        else if (o.getCreateIndex() != null)
        {
            handle(ctx, o.getCreateIndex()
                    .getRelOp(), node);
        }
        else if (o.getConstTableGet() != null)
        {
            for (RelOpType type : o.getConstTableGet()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getSegment() != null)
        {
            handle(ctx, o.getSegment()
                    .getRelOp(), node);
        }
        else if (o.getSequence() != null)
        {
            for (RelOpType type : o.getSequence()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getBitmap() != null)
        {
            handle(ctx, o.getBitmap()
                    .getRelOp(), node);
        }
        else if (o.getSpool() != null)
        {
            handle(ctx, o.getSpool()
                    .getRelOp(), node);
        }
        else if (o.getStreamAggregate() != null)
        {
            node.properties()
                    .add(getColumnList("", "GroupBy", false, o.getStreamAggregate()
                            .getGroupBy()));
            handle(ctx, o.getStreamAggregate()
                    .getRelOp(), node);
        }
        else if (o.getJoin() != null)
        {
            for (RelOpType type : o.getJoin()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getApply() != null)
        {
            for (RelOpType type : o.getApply()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getCollapse() != null)
        {
            handle(ctx, o.getCollapse()
                    .getRelOp(), node);
        }
        else if (o.getWindowSpool() != null)
        {
            handle(ctx, o.getWindowSpool()
                    .getRelOp(), node);
        }
        else if (o.getWindowAggregate() != null)
        {
            handle(ctx, o.getWindowAggregate()
                    .getRelOp(), node);
        }
        else if (o.getSwitch() != null)
        {
            for (RelOpType type : o.getSwitch()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getSplit() != null)
        {
            handle(ctx, o.getSplit()
                    .getRelOp(), node);
        }
        else if (o.getRemoteFetch() != null)
        {
            handle(ctx, o.getRemoteFetch()
                    .getRelOp(), node);
        }
        else if (o.getRemoteModify() != null)
        {
            handle(ctx, o.getRemoteModify()
                    .getRelOp(), node);
        }
        else if (o.getExternalSelect() != null)
        {
            for (RelOpType type : o.getExternalSelect()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getGbApply() != null)
        {
            for (RelOpType type : o.getGbApply()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getDelete() != null)
        {
            for (RelOpType type : o.getDelete()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getInsert() != null)
        {
            for (RelOpType type : o.getInsert()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getLocalCube() != null)
        {
            for (RelOpType type : o.getLocalCube()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getGbAgg() != null)
        {
            for (RelOpType type : o.getGbAgg()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getGeneric() != null)
        {
            for (RelOpType type : o.getGeneric()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getMove() != null)
        {
            for (RelOpType type : o.getMove()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getBatchHashTableBuild() != null)
        {
            handle(ctx, o.getBatchHashTableBuild()
                    .getRelOp(), node);
        }
        else if (o.getProject() != null)
        {
            for (RelOpType type : o.getProject()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getForeignKeyReferencesCheck() != null)
        {
            handle(ctx, o.getForeignKeyReferencesCheck()
                    .getRelOp(), node);
        }
        else if (o.getMergeInterval() != null)
        {
            handle(ctx, o.getMergeInterval()
                    .getRelOp(), node);
        }
        else if (o.getExtension() != null)
        {
            handle(ctx, o.getExtension()
                    .getRelOp(), node);
        }
        else if (o.getUnion() != null)
        {
            for (RelOpType type : o.getUnion()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getUnionAll() != null)
        {
            for (RelOpType type : o.getUnionAll()
                    .getRelOp())
            {
                handle(ctx, type, node);
            }
        }
        else if (o.getAssert() != null)
        {
            handle(ctx, o.getAssert()
                    .getRelOp(), node);
        }
        return parent;
    };

    private static NodeHandler<QueryPlanType> QueryPlanType_Handler = (Context ctx, QueryPlanType o, Node parent) ->
    {
        parent.properties()
                .add(new Node.NodeProperty("Cache Plan Size", o.getCachedPlanSize()));
        parent.properties()
                .add(new Node.NodeProperty("Compiler CPU", o.getCompileCPU()));
        parent.properties()
                .add(new Node.NodeProperty("Compiler Memory", o.getCompileMemory()));
        parent.properties()
                .add(new Node.NodeProperty("Compile Time", o.getCompileTime()));
        parent.properties()
                .add(new Node.NodeProperty("DegreeOfParallelism", o.getDegreeOfParallelism()));
        OptimizerStatsUsageType statsUsage = o.getOptimizerStatsUsage();
        if (statsUsage != null)
        {
            Node.NodeProperty statsUsageProp = new Node.NodeProperty("OptimizerStatsUsage", o.getCompileTime());
            parent.properties()
                    .add(statsUsageProp);
            int index = 0;
            for (StatsInfoType sit : statsUsage.getStatisticsInfo())
            {
                Node.NodeProperty prop = new Node.NodeProperty("[" + index++ + "]", null);
                prop.subProperties()
                        .add(new Node.NodeProperty("Database", sit.getDatabase()));
                prop.subProperties()
                        .add(new Node.NodeProperty("Schema", sit.getSchema()));
                prop.subProperties()
                        .add(new Node.NodeProperty("Table", sit.getTable()));
                prop.subProperties()
                        .add(new Node.NodeProperty("Statistics", sit.getStatistics()));
                prop.subProperties()
                        .add(new Node.NodeProperty("ModificationCount", sit.getModificationCount()));
                prop.subProperties()
                        .add(new Node.NodeProperty("SamplingPercent", sit.getSamplingPercent()));
                prop.subProperties()
                        .add(new Node.NodeProperty("LastUpdate", sit.getLastUpdate()));
                statsUsageProp.subProperties()
                        .add(prop);
            }
        }

        if (o.getParameterList() != null)
        {
            NodeProperty parameters = new Node.NodeProperty("Parameters", null);
            parent.properties()
                    .add(parameters);
            for (ColumnReferenceType column : o.getParameterList()
                    .getColumnReference())
            {
                NodeProperty prop = new Node.NodeProperty(getColumnReferenceLabel(column), null);
                parameters.subProperties()
                        .add(prop);
                addColumnReferenceSubProperties(prop, column);
            }
        }

        return handle(ctx, o.getRelOp(), parent);
    };

    private static NodeProperty getMissingIndicesProperty(MissingIndexesType type)
    {
        NodeProperty prop = new NodeProperty(WARNINGS, "Missing Indices", null, true);
        if (type == null)
        {
            return prop;
        }

        int index = 0;
        for (MissingIndexGroupType gt : type.getMissingIndexGroup())
        {
            NodeProperty miProp = new NodeProperty("[" + index++ + "]", null);
            prop.subProperties()
                    .add(miProp);

            double impact = gt.getImpact();
            miProp.subProperties()
                    .add(new NodeProperty("Impact", impact));

            MissingIndexType mit = gt.getMissingIndex()
                    .get(0);

            miProp.subProperties()
                    .add(new NodeProperty("Database", mit.getDatabase()));
            miProp.subProperties()
                    .add(new NodeProperty("Schema", mit.getSchema()));
            miProp.subProperties()
                    .add(new NodeProperty("Table", mit.getTable()));

            String indexDef = "";
            String include = "";
            String columns = "";

            for (ColumnGroupType cgt : mit.getColumnGroup())
            {
                String tmp = cgt.getColumn()
                        .stream()
                        .map(ColumnType::getName)
                        .collect(joining(","));

                miProp.subProperties()
                        .add(new NodeProperty(cgt.getUsage(), tmp));

                if ("include".equalsIgnoreCase(cgt.getUsage()))
                {
                    include = tmp;
                }
                else
                {
                    columns = tmp;
                }
            }

            String table = List.of(mit.getDatabase(), mit.getSchema(), mit.getTable())
                    .stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(joining("."));
            indexDef = "CREATE NONCLUSTERED INDEX [IndexName] ON %s (%s)".formatted(table, columns);
            if (!isBlank(include))
            {
                indexDef += " INCLUDE (%s)".formatted(include);
            }

            miProp.subProperties()
                    .add(new NodeProperty("", "Create Index Def.", indexDef, indexDef, false, List.of()));
        }

        return prop;
    }

    private static NodeHandler<StmtCondType> StmtCondType_Handler = (Context ctx, StmtCondType o, Node parent) ->
    {
        String label = WordUtils.capitalizeFully(o.getStatementType());

        List<Node.NodeProperty> properties = new ArrayList<>();
        addBasetStmInfoProperties(o, properties);

        Node node = new Node("<html><b>" + label + "</b>", "", properties);
        if (parent != null)
        {
            parent.children()
                    .add(new Node.NodeLink("", List.of(), node));
        }
        if (o.getCondition()
                .getQueryPlan() != null)
        {
            handle(ctx, o.getCondition()
                    .getQueryPlan(), node);
        }
        if (o.getThen()
                .getStatements() != null)
        {
            for (BaseStmtInfoType stmInfo : o.getThen()
                    .getStatements()
                    .getStmtSimpleOrStmtCondOrStmtCursor())
            {
                handle(ctx, stmInfo, node);
            }
        }
        if (o.getElse() != null)
        {
            for (BaseStmtInfoType stmInfo : o.getElse()
                    .getStatements()
                    .getStmtSimpleOrStmtCondOrStmtCursor())
            {
                handle(ctx, stmInfo, node);
            }
        }

        return node;
    };

    private static void addBasetStmInfoProperties(BaseStmtInfoType type, List<Node.NodeProperty> properties)
    {
        properties.add(new Node.NodeProperty("BatchModeOnRowStoreUsed", type.isBatchModeOnRowStoreUsed()));
        properties.add(new Node.NodeProperty("CardinalityEstimationModelVersion", type.getCardinalityEstimationModelVersion()));
        properties.add(new Node.NodeProperty("", "Statement", type.getStatementText(), type.getStatementText(), false, List.of()));
        properties.add(new Node.NodeProperty(NodeProperty.STATEMENT_TEXT, type.getStatementText()));

        if (type.getStatementSetOptions() != null)
        {
            NodeProperty options = new NodeProperty("Options", null);
            properties.add(options);

            options.subProperties()
                    .add(new NodeProperty("ANSI_NULLS", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isANSINULLS())));
            options.subProperties()
                    .add(new NodeProperty("ANSI_PADDING", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isANSIPADDING())));
            options.subProperties()
                    .add(new NodeProperty("ANSI_WARNINGS", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isANSIWARNINGS())));
            options.subProperties()
                    .add(new NodeProperty("ARITHABORT", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isARITHABORT())));
            options.subProperties()
                    .add(new NodeProperty("CONCAT_NULL_YIELDS_NULL", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isCONCATNULLYIELDSNULL())));
            options.subProperties()
                    .add(new NodeProperty("NUMERIC_ROUNDABORT", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isNUMERICROUNDABORT())));
            options.subProperties()
                    .add(new NodeProperty("QUOTED_IDENTIFIER", BooleanUtils.isTrue(type.getStatementSetOptions()
                            .isQUOTEDIDENTIFIER())));
        }
    }

    private static NodeHandler<StmtSimpleType> StmtSimpleType_Handler = (Context ctx, StmtSimpleType o, Node parent) ->
    {
        List<Node.NodeProperty> properties = new ArrayList<>();
        if (o.getQueryPlan() != null)
        {
            ctx.rootEstimatedTotalSubtreeCost = o.getQueryPlan()
                    .getRelOp()
                    .getEstimatedTotalSubtreeCost();
        }

        addBasetStmInfoProperties(o, properties);
        WarningsType warnings = o.getQueryPlan() != null ? o.getQueryPlan()
                .getWarnings()
                : null;
        MissingIndexesType missingIndxeses = o.getQueryPlan() != null ? o.getQueryPlan()
                .getMissingIndexes()
                : null;

        String label = "<html><b>" + WordUtils.capitalizeFully(o.getStatementType()) + "</b>";
        if (o.getStoredProc() != null)
        {
            label += "<br/>" + o.getStoredProc()
                    .getProcName();
        }
        if (o.getQueryPlan() != null)
        {
            properties.add(new NodeProperty("EstimatedTotalSubtreeCost", o.getQueryPlan()
                    .getRelOp()
                    .getEstimatedTotalSubtreeCost(), true));
        }

        if (warnings != null
                || missingIndxeses != null)
        {
            properties.add(new NodeProperty(NodeProperty.HAS_WARNINGS, true));
            if (warnings != null)
            {
                properties.add(getWarningsProperty(warnings));
            }
            if (missingIndxeses != null)
            {
                properties.add(getMissingIndicesProperty(missingIndxeses));
            }
        }

        Node node = new Node(label, "", properties);

        if (parent != null)
        {
            parent.children()
                    .add(new Node.NodeLink("", List.of(), node));
        }
        if (o.getQueryPlan() != null)
        {
            return handle(ctx, o.getQueryPlan(), node);
        }
        else if (o.getStoredProc() != null)
        {
            for (BaseStmtInfoType stmInfo : o.getStoredProc()
                    .getStatements()
                    .getStmtSimpleOrStmtCondOrStmtCursor())
            {
                handle(ctx, stmInfo, node);
            }
        }
        return node;
    };

    private static final Map<Class<?>, NodeHandler<?>> HANDLERS = new HashMap<>();
    static
    {
        HANDLERS.put(StmtSimpleType.class, StmtSimpleType_Handler);
        HANDLERS.put(StmtCondType.class, StmtCondType_Handler);
        HANDLERS.put(QueryPlanType.class, QueryPlanType_Handler);
        HANDLERS.put(TopType.class, TopType_Handler);
        HANDLERS.put(RelOpType.class, RelOpType_Handler);
        HANDLERS.put(MergeType.class, MergeType_Handler);
        HANDLERS.put(IndexScanType.class, IndexScanType_Handler);
        HANDLERS.put(HashType.class, HashType_Handler);
        HANDLERS.put(NestedLoopsType.class, NestedLoopsType_Handler);
        HANDLERS.put(AdaptiveJoinType.class, AdaptiveJoinType_Handler);
        HANDLERS.put(ParallelismType.class, ParallelismType_Handler);
        HANDLERS.put(TableScanType.class, TableScanType_Handler);
        HANDLERS.put(ConcatType.class, ConcatType_Handler);
        HANDLERS.put(ConstantScanType.class, ConstantScanType_Handler);
        HANDLERS.put(ComputeScalarType.class, ComputeScalarType_Handler);
        HANDLERS.put(FilterType.class, FilterType_Handler);
        HANDLERS.put(TableValuedFunctionType.class, TableValuedFunctionType_Handler);
        HANDLERS.put(SortType.class, SortType_Handler);
        HANDLERS.put(TopSortType.class, SortType_Handler);
    }

    private static NodeProperty getScalarExpressionProperty(String label, ScalarExpressionType expression)
    {
        return new NodeProperty("", label, null, expression == null ? null
                : expression.getScalarOperator()
                        .getScalarString(),
                false, List.of());
    }

    private static NodeProperty getWarningsProperty(WarningsType warnings)
    {
        NodeProperty result = new NodeProperty(WARNINGS, WARNINGS, null, null, true, new ArrayList<>());
        if (warnings != null)
        {
            result.subProperties()
                    .add(new NodeProperty("NoJoinPredicate", warnings.isNoJoinPredicate()));
            result.subProperties()
                    .add(new NodeProperty("SpatialGuess", warnings.isSpatialGuess()));
            result.subProperties()
                    .add(new NodeProperty("UnmatchedIndexes", warnings.isUnmatchedIndexes()));
            result.subProperties()
                    .add(new NodeProperty("FullUpdateForOnlineIndexBuild", warnings.isFullUpdateForOnlineIndexBuild()));

            for (Object obj : warnings.getSpillOccurredOrColumnsWithNoStatisticsOrSpillToTempDb())
            {
                if (obj instanceof SpillOccurredType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "SpillOccurred", "", t.isDetail()));
                }
                else if (obj instanceof ColumnReferenceListType t)
                {
                    result.subProperties()
                            .add(getColumnList(WARNINGS, "ColumnsWithNoStatistics", true, t));
                }
                else if (obj instanceof SpillToTempDbType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "SpillToTempDb", null, null, true,
                                    List.of(new NodeProperty("SpillLevel", t.getSpillLevel()), new NodeProperty("SpilledThreadCount", t.getSpilledThreadCount()))));
                }
                else if (obj instanceof WaitWarningType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "Wait", null, null, true, List.of(new NodeProperty("WaitType", t.getWaitType()), new NodeProperty("WaitTime", t.getWaitTime()))));
                }
                else if (obj instanceof AffectingConvertWarningType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "PlanAffectingConvert", null, null, true,
                                    List.of(new NodeProperty("ConvertIssue", t.getConvertIssue()), new NodeProperty("Expression", t.getExpression()))));
                }
                else if (obj instanceof SortSpillDetailsType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "SortSpillDetails", null, null, true,
                                    List.of(new NodeProperty("GrantedMemoryKb", t.getGrantedMemoryKb()), new NodeProperty("UsedMemoryKb", t.getUsedMemoryKb()),
                                            new NodeProperty("WritesToTempDb", t.getWritesToTempDb()), new NodeProperty("ReadsFromTempDb", t.getReadsFromTempDb()))));
                }
                else if (obj instanceof HashSpillDetailsType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "HashSpillDetails", null, null, true,
                                    List.of(new NodeProperty("GrantedMemoryKb", t.getGrantedMemoryKb()), new NodeProperty("UsedMemoryKb", t.getUsedMemoryKb()),
                                            new NodeProperty("WritesToTempDb", t.getWritesToTempDb()), new NodeProperty("ReadsFromTempDb", t.getReadsFromTempDb()))));
                }
                else if (obj instanceof ExchangeSpillDetailsType t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "HashSpillDetails", null, null, true, List.of(new NodeProperty("WritesToTempDb", t.getWritesToTempDb()))));
                }
                else if (obj instanceof MemoryGrantWarningInfo t)
                {
                    result.subProperties()
                            .add(new NodeProperty(WARNINGS, "HashSpillDetails", null, null, true,
                                    List.of(new NodeProperty("GrantWarningKind", t.getGrantWarningKind()), new NodeProperty("RequestedMemory", t.getRequestedMemory()),
                                            new NodeProperty("GrantedMemory", t.getGrantedMemory()), new NodeProperty("MaxUsedMemory", t.getMaxUsedMemory()))));
                }
            }
        }

        return result;
    }

    private static NodeProperty getDefinedValues(DefinedValuesListType list)
    {
        NodeProperty result = new NodeProperty("Defined Values", null);
        if (list == null)
        {
            return result;
        }
        int index = 0;
        for (DefinedValue type : list.getDefinedValue())
        {
            if (type.getColumnReference()
                    .isEmpty())
            {
                continue;
            }

            NodeProperty prop = new NodeProperty("[" + index++ + "]", getColumnReferenceLabel(type.getColumnReference()
                    .get(0)));
            result.subProperties()
                    .add(prop);

            NodeProperty column = new NodeProperty("Column", getColumnReferenceLabel(type.getColumnReference()
                    .get(0)));
            prop.subProperties()
                    .add(column);
            if (type.getScalarOperator() != null)
            {
                prop.subProperties()
                        .add(new NodeProperty("ScalarOperator", type.getScalarOperator()
                                .getScalarString()));
            }
            addColumnReferenceSubProperties(column, type.getColumnReference()
                    .get(0));

        }

        return result;
    }

    private static String getObjectLabel(ObjectType object, boolean shortLabel)
    {
        List<String> parts = shortLabel ? Arrays.asList(object.getTable(), object.getIndex())
                : Arrays.asList(object.getDatabase(), object.getSchema(), object.getTable(), object.getIndex());

        return parts.stream()
                .filter(StringUtils::isNotBlank)
                .collect(joining("."));
    }

    private static String getColumnReferenceLabel(ColumnReferenceType type)
    {
        return Arrays.asList(type.getServer(), type.getDatabase(), type.getSchema(), type.getTable(), type.getColumn())
                .stream()
                .filter(Objects::nonNull)
                .collect(joining("."));
    }

    private static NodeProperty getObject(ObjectType object)
    {
        NodeProperty result = new NodeProperty("Object", getObjectLabel(object, false));
        result.subProperties()
                .add(new NodeProperty("Server", object.getServer()));
        result.subProperties()
                .add(new NodeProperty("Database", object.getDatabase()));
        result.subProperties()
                .add(new NodeProperty("Schema", object.getSchema()));
        result.subProperties()
                .add(new NodeProperty("Table", object.getTable()));
        result.subProperties()
                .add(new NodeProperty("Index", object.getIndex()));
        result.subProperties()
                .add(new NodeProperty("IndexKind", object.getIndexKind()));
        result.subProperties()
                .add(new NodeProperty("Filtered", object.isFiltered()));
        result.subProperties()
                .add(new NodeProperty("Alias", object.getAlias()));
        return result;
    }

    private static void addColumnReferenceSubProperties(NodeProperty prop, ColumnReferenceType type)
    {
        if (type == null)
        {
            return;
        }
        prop.subProperties()
                .add(new NodeProperty("ScalarOperator", type.getScalarOperator()));
        prop.subProperties()
                .add(new NodeProperty("Server", type.getServer()));
        prop.subProperties()
                .add(new NodeProperty("Database", type.getDatabase()));
        prop.subProperties()
                .add(new NodeProperty("Schema", type.getSchema()));
        prop.subProperties()
                .add(new NodeProperty("Table", type.getTable()));
        prop.subProperties()
                .add(new NodeProperty("Alias", type.getAlias()));
        prop.subProperties()
                .add(new NodeProperty("Column", type.getColumn()));
        prop.subProperties()
                .add(new NodeProperty("ComputedColumn", type.isComputedColumn()));
        prop.subProperties()
                .add(new NodeProperty("ParameterDataType", type.getParameterDataType()));
        prop.subProperties()
                .add(new NodeProperty("ParameterCompiledValue", type.getParameterCompiledValue()));
        prop.subProperties()
                .add(new NodeProperty("ParameterRuntimeValue", type.getParameterRuntimeValue()));

    }

    private static NodeProperty getColumnList(String category, String name, boolean includeInToolTip, ColumnReferenceListType list)
    {
        NodeProperty outputList = new NodeProperty(category, name, null, null, includeInToolTip, new ArrayList<>());

        if (list != null)
        {
            int index = 0;
            for (ColumnReferenceType type : list.getColumnReference())
            {
                NodeProperty prop = new NodeProperty("[" + index++ + "]", getColumnReferenceLabel(type));
                outputList.subProperties()
                        .add(prop);

                addColumnReferenceSubProperties(prop, type);
            }
        }

        return outputList;
    }

    interface NodeHandler<T>
    {
        Node handle(Context context, T node, Node parent);
    }

    private static class NamespaceFilter extends XMLFilterImpl
    {
        private String usedNamespaceUri;
        private boolean addNamespace;

        // State variable
        private boolean addedNamespace = false;

        public NamespaceFilter(String namespaceUri, boolean addNamespace)
        {
            super();

            if (addNamespace)
                this.usedNamespaceUri = namespaceUri;
            else
                this.usedNamespaceUri = "";
            this.addNamespace = addNamespace;
        }

        @Override
        public void startDocument() throws SAXException
        {
            super.startDocument();
            if (addNamespace)
            {
                startControlledPrefixMapping();
            }
        }

        @Override
        public void startElement(String arg0, String arg1, String arg2, Attributes arg3) throws SAXException
        {

            super.startElement(this.usedNamespaceUri, arg1, arg2, arg3);
        }

        @Override
        public void endElement(String arg0, String arg1, String arg2) throws SAXException
        {

            super.endElement(this.usedNamespaceUri, arg1, arg2);
        }

        @Override
        public void startPrefixMapping(String prefix, String url) throws SAXException
        {

            if (addNamespace)
            {
                this.startControlledPrefixMapping();
            }
            else
            {
                // Remove the namespace, i.e. don´t call startPrefixMapping for parent!
            }

        }

        private void startControlledPrefixMapping() throws SAXException
        {

            if (this.addNamespace
                    && !this.addedNamespace)
            {
                // We should add namespace since it is set and has not yet been done.
                super.startPrefixMapping("", this.usedNamespaceUri);

                // Make sure we dont do it twice
                this.addedNamespace = true;
            }
        }

    }
}
