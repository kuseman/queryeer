package com.queryeer.output.graph;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnitSource;
import org.jfree.chart.axis.TickUnitSource;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.xy.ClusteredXYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;

import se.kuseman.payloadbuilder.api.execution.EpochDateTime;
import se.kuseman.payloadbuilder.api.execution.EpochDateTimeOffset;
import se.kuseman.payloadbuilder.api.execution.UTF8String;

/** Chart that displays data as a histogram buckets. */
class GraphComponent extends JPanel
{
    private static final Cursor HAND_CURSOR = Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR);
    private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();
    /** Null marker in graph */
    private static final Object NULL = new Object()
    {
        @Override
        public String toString()
        {
            return "NULL";
        }
    };
    private final IQueryFile queryFile;
    private final JTextField tfXColumn;
    private final JTextField tfYColumns;
    private final JCheckBox cbDomainIsTimeSeries;
    private final JTextField tfBreakDownColumns;
    private final JComboBox<Interval> cbInterval;
    private final JComboBox<GraphType> cbGraphType;
    private final JComboBox<ValueType> cbValueType;
    private final JComboBox<Function> cbFunction;
    private final JPanel chartPanel;

    private GraphSettings settings;

    // Timeseries mode
    private QTimeSeriesCollection timeSeriesDataset;
    private QDefaultCategoryDataset categoryDataset;
    private TimeSeries countTimeSeries;
    private Map<Object, TimeSeries> valueTimeSeries = new HashMap<>();
    private Map<RegularTimePeriod, Integer> bucketStartRow = new HashMap<>();
    private int lastNotifyRowNumber;
    private boolean validationError;

    static class QTimeSeriesCollection extends TimeSeriesCollection
    {
        QTimeSeriesCollection()
        {
            // Turn off notify to be able to control when we want to notify/redraw
            setNotify(false);
        }

        void notifyEx()
        {
            notifyListeners(new DatasetChangeEvent(this, this));
        }
    }

    static class QDefaultCategoryDataset extends DefaultCategoryDataset
    {
        QDefaultCategoryDataset()
        {
            // Turn off notify to be able to control when we want to notify/redraw
            setNotify(false);
        }

        void notifyEx()
        {
            notifyListeners(new DatasetChangeEvent(this, this));
        }
    }

    static class QTimeSeriesDataItem extends TimeSeriesDataItem implements DataItem
    {
        // Context data used by Functions
        private int count = 1;
        private Number sum = 0;

        QTimeSeriesDataItem(RegularTimePeriod period, Number value)
        {
            super(period, value);
        }

        @Override
        public Number getValue()
        {
            return super.getValue();
        }

        @Override
        public int getCount()
        {
            return count;
        }

        @Override
        public Number getSum()
        {
            return sum;
        }

        @Override
        public void setSum(Number value)
        {
            this.sum = value;
        }

        @Override
        public Object clone()
        {
            return this;
        }
    }

    static class QNumber extends Number implements DataItem
    {
        private int count = 0;
        private Number sum = 0;
        private Number value = 0;

        void setValue(Number value)
        {
            if (value instanceof QNumber q)
            {
                this.value = q.value;
            }
            else
            {
                this.value = value;
            }
        }

        @Override
        public int getCount()
        {
            return count;
        }

        @Override
        public Number getSum()
        {
            return sum;
        }

        @Override
        public Number getValue()
        {
            return value;
        }

        @Override
        public void setSum(Number value)
        {
            this.sum = value;
        }

        @Override
        public int intValue()
        {
            return value.intValue();
        }

        @Override
        public long longValue()
        {
            return value.longValue();
        }

        @Override
        public float floatValue()
        {
            return value.floatValue();
        }

        @Override
        public double doubleValue()
        {
            return value.doubleValue();
        }
    }

    static interface DataItem
    {
        int getCount();

        Number getSum();

        Number getValue();

        void setSum(Number value);
    }

    GraphComponent(IQueryFile queryFile)
    {
        super(new GridBagLayout());
        this.queryFile = queryFile;

        cbGraphType = new JComboBox<>(GraphType.values());

        tfXColumn = new JTextField(30);
        tfXColumn.setText("@timestamp");
        tfXColumn.setToolTipText("The column that should generate the X axis value.");

        cbDomainIsTimeSeries = new JCheckBox();
        cbDomainIsTimeSeries.setSelected(true);
        cbDomainIsTimeSeries.setToolTipText("If checked the X axis column is treated as a datetime value.");

        cbInterval = new JComboBox<>(Interval.values());
        cbInterval.setSelectedItem(Interval.HOUR);
        cbInterval.setToolTipText("The interval of the X axis datetime.");

        cbValueType = new JComboBox<>(ValueType.values());
        cbValueType.setSelectedItem(ValueType.INTEGER);

        cbFunction = new JComboBox<>(Function.values());
        cbFunction.setSelectedItem(Function.SUM);

        // CSOFF
        tfYColumns = new JTextField(100);
        tfYColumns.setToolTipText(
                "<html>The columns that should generate the Y axis values. <br/>NOTE! Only one column is supported when break down columns is set, then the value column for each unique break down value will be generated.");

        tfBreakDownColumns = new JTextField(30);
        tfBreakDownColumns.setToolTipText("Specify columns that should generate Y axis series. Each unique value combination of the columns will generate a Y series.");
        // CSON

        chartPanel = new JPanel(new BorderLayout());
        chartPanel.setBorder(BorderFactory.createEmptyBorder());

        cbDomainIsTimeSeries.addActionListener(l -> cbInterval.setEnabled(cbDomainIsTimeSeries.isSelected()));

        int x = 0;
        add(new JLabel("Graph Type:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 3, 0));
        add(cbGraphType, new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

        add(new JLabel("X column:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 3, 0, 0), 3, 0));
        add(tfXColumn, new GridBagConstraints(x++, 0, 1, 1, 0.05d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

        add(new JLabel("Time Series:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 3, 0, 0), 3, 0));
        add(cbDomainIsTimeSeries, new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

        add(new JLabel("Interval:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 10, 0, 0), 3, 0));
        add(cbInterval, new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 10, 0));

        add(new JLabel("Y Columns:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 10, 0, 0), 3, 0));
        add(tfYColumns, new GridBagConstraints(x++, 0, 1, 1, 0.05d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 10, 0));

        add(new JLabel("Value Type:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 10, 0, 0), 3, 0));
        add(cbValueType, new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 10, 0));

        add(new JLabel("Function:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 10, 0, 0), 3, 0));
        add(cbFunction, new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 10, 0));

        add(new JLabel("Break Down Columns:"), new GridBagConstraints(x++, 0, 1, 1, 0.0d, 0.0d, GridBagConstraints.BASELINE, GridBagConstraints.NONE, new Insets(3, 10, 0, 0), 3, 0));
        add(tfBreakDownColumns, new GridBagConstraints(x++, 0, 1, 1, 0.05d, 0.0d, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 3), 3, 0));

        add(chartPanel, new GridBagConstraints(0, 1, x, 1, 1.0d, 1.0d, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    }

    void clearState()
    {
        if (timeSeriesDataset != null)
        {
            timeSeriesDataset.removeAllSeries();
        }
        if (categoryDataset != null)
        {
            categoryDataset.clear();
        }
        bucketStartRow.clear();
        countTimeSeries = null;
        valueTimeSeries.clear();
        settings = null;
        lastNotifyRowNumber = 0;
        validationError = false;
        chartPanel.removeAll();
        chartPanel.invalidate();
        chartPanel.repaint();
        validationError = false;
    }

    void addRow(int rowNumber, List<String> rowColumns, List<Object> rowValues)
    {
        // Initalize graph etc.
        if (!validationError
                && settings == null)
        {
            settings = getSettings();
            try
            {
                SwingUtilities.invokeAndWait(() -> initalizeGraph());
            }
            catch (InvocationTargetException | InterruptedException e)
            {
            }
        }

        if (validationError)
        {
            return;
        }

        if (settings.xisTimeSeries)
        {
            populateTimeSeriesData(rowNumber, rowColumns, rowValues);
        }
        else
        {
            populateCategoryChart(rowNumber, rowColumns, rowValues);
        }
    }

    void endResult()
    {
        if (lastNotifyRowNumber > 0)
        {
            notifyChart();
        }
    }

    private GraphSettings getSettings()
    {
        //@formatter:off
        return new GraphSettings(
                (GraphType) cbGraphType.getSelectedItem(),
                lowerCase(tfXColumn.getText()),
                cbDomainIsTimeSeries.isSelected(),
                (Interval) cbInterval.getSelectedItem(),
                isBlank(tfYColumns.getText()) 
                    ? emptyList()
                    : Arrays.stream(StringUtils.split(tfYColumns.getText(), ','))
                     .map(StringUtils::trim)
                     .filter(StringUtils::isNotBlank)
                     .map(StringUtils::lowerCase)
                     .toList(),
                isBlank(tfBreakDownColumns.getText())
                    ? emptyList()
                    : Arrays.stream(StringUtils.split(tfBreakDownColumns.getText(), ','))
                    .map(StringUtils::trim)
                    .filter(StringUtils::isNotBlank)
                    .map(StringUtils::lowerCase)
                    .toList(),
                (ValueType) cbValueType.getSelectedItem(),
                (Function) cbFunction.getSelectedItem());
        //@formatter:on
    }

    // NOTE! Must be executed in EDT
    private void initalizeGraph()
    {
        chartPanel.removeAll();

        if (!settings.breakDownColumns.isEmpty()
                && settings.valueColumns.size() > 1)
        {
            JOptionPane.showMessageDialog(this, "Cannot have multiple value columns when break down columns is set!", "Error", JOptionPane.ERROR_MESSAGE);
            validationError = true;
            return;
        }

        if (settings.xisTimeSeries)
        {
            initalizeTimeSeriesGraph();
        }
        else
        {
            initalizeCategoryGraph();
        }
    }

    private void initalizeTimeSeriesGraph()
    {
        timeSeriesDataset = new QTimeSeriesCollection();

        // No value columns => create a count series
        // No breakdown column => create a series per value column
        // Break down column => series are created on the fly

        if (settings.valueColumns.isEmpty()
                && settings.breakDownColumns.isEmpty())
        {
            countTimeSeries = new TimeSeries(settings.xcolumn);
            timeSeriesDataset.addSeries(countTimeSeries);
        }
        else if (settings.breakDownColumns.isEmpty())
        {
            for (String valueColumn : settings.valueColumns)
            {
                TimeSeries series = new TimeSeries(valueColumn);
                valueTimeSeries.put(valueColumn, series);
                timeSeriesDataset.addSeries(series);
            }
        }

        DateAxis domain = new DateAxis("Time");
        NumberAxis range = new NumberAxis("");
        domain.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        range.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        domain.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
        range.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));

        XYItemRenderer renderer;
        if (settings.graphType == GraphType.BAR)
        {
            renderer = new ClusteredXYBarRenderer();
            ((ClusteredXYBarRenderer) renderer).setBarAlignmentFactor(0.5);
            ((ClusteredXYBarRenderer) renderer).setMargin(0.1);
        }
        else
        {
            renderer = new XYLineAndShapeRenderer(true, false);
        }

        XYPlot plot = new XYPlot(timeSeriesDataset, domain, range, renderer);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        domain.setAutoRange(true);
        domain.setAutoTickUnitSelection(true);
        domain.setLowerMargin(0.0);
        domain.setUpperMargin(0.0);
        domain.setTickLabelsVisible(true);
        domain.setTickUnit(settings.interval.tickUnit, false, true);
        domain.setDateFormatOverride(settings.interval.tickDateFormat);
        range.setStandardTickUnits(settings.valueType.tickUnit);

        JFreeChart chart = new JFreeChart("", new Font("SansSerif", Font.BOLD, 24), plot, true);
        StandardXYToolTipGenerator toolTipGenerator = StandardXYToolTipGenerator.getTimeSeriesInstance();
        renderer.setDefaultToolTipGenerator(toolTipGenerator);
        chart.setBackgroundPaint(Color.white);
        ChartPanel jfChartPanel = new ChartPanel(chart);

        jfChartPanel.addChartMouseListener(new ChartMouseListener()
        {
            @Override
            public void chartMouseMoved(ChartMouseEvent event)
            {
                boolean bar = event.getEntity() instanceof XYItemEntity;
                setCursor(bar ? HAND_CURSOR
                        : DEFAULT_CURSOR);
            }

            @Override
            public void chartMouseClicked(ChartMouseEvent event)
            {
                // event.getEntity().
                if (event.getEntity() instanceof XYItemEntity xyie)
                {
                    int index = xyie.getItem();
                    RegularTimePeriod timePeriod = timeSeriesDataset.getSeries(0)
                            .getTimePeriod(index);

                    ITableOutputComponent table = queryFile.getOutputComponent(ITableOutputComponent.class);
                    table.selectRow(0, bucketStartRow.get(timePeriod));

                    queryFile.selectOutputComponent(ITableOutputComponent.class);
                }
            }
        });

        jfChartPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4), BorderFactory.createLineBorder(Color.black)));
        chartPanel.add(new JScrollPane(jfChartPanel), BorderLayout.CENTER);

        timeSeriesDataset.notifyEx();
    }

    private void initalizeCategoryGraph()
    {
        categoryDataset = new QDefaultCategoryDataset();

        CategoryAxis categoryAxis = new CategoryAxis(settings.xcolumn);
        NumberAxis valueAxis = new NumberAxis("");
        categoryAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        valueAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        categoryAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));
        valueAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 14));

        CategoryItemRenderer renderer;
        if (settings.graphType == GraphType.BAR)
        {
            renderer = new BarRenderer();
        }
        else
        {
            renderer = new LineAndShapeRenderer(true, false);
        }

        renderer.setDefaultToolTipGenerator(new StandardCategoryToolTipGenerator());

        CategoryPlot plot = new CategoryPlot(categoryDataset, categoryAxis, valueAxis, renderer);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        categoryAxis.setLowerMargin(0.0);
        categoryAxis.setUpperMargin(0.0);
        categoryAxis.setTickLabelsVisible(true);
        valueAxis.setStandardTickUnits(settings.valueType.tickUnit);
        JFreeChart chart = new JFreeChart("", new Font("SansSerif", Font.BOLD, 24), plot, true);
        chart.setBackgroundPaint(Color.white);
        ChartPanel jfChartPanel = new ChartPanel(chart);

        jfChartPanel.addChartMouseListener(new ChartMouseListener()
        {
            @Override
            public void chartMouseMoved(ChartMouseEvent event)
            {
                boolean bar = event.getEntity() instanceof XYItemEntity;
                setCursor(bar ? HAND_CURSOR
                        : DEFAULT_CURSOR);
            }

            @Override
            public void chartMouseClicked(ChartMouseEvent event)
            {
                // event.getEntity().
                if (event.getEntity() instanceof XYItemEntity xyie)
                {
                    int index = xyie.getItem();
                    RegularTimePeriod timePeriod = timeSeriesDataset.getSeries(0)
                            .getTimePeriod(index);

                    ITableOutputComponent table = queryFile.getOutputComponent(ITableOutputComponent.class);
                    table.selectRow(0, bucketStartRow.get(timePeriod));

                    queryFile.selectOutputComponent(ITableOutputComponent.class);
                }
            }
        });

        jfChartPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4), BorderFactory.createLineBorder(Color.black)));
        chartPanel.add(new JScrollPane(jfChartPanel), BorderLayout.CENTER);

        categoryDataset.notifyEx();
    }

    private void populateTimeSeriesData(int rowNumber, List<String> rowColumns, List<Object> rowValues)
    {
        boolean hasBreakDownSetting = !settings.breakDownColumns.isEmpty();

        // No value columns -> count
        // Break down column -> switch time series
        // value columns -> many series with many values

        TimeSeries[] timeSeries;
        Object[] values;

        // Count
        if (settings.valueColumns.isEmpty()
                && !hasBreakDownSetting)
        {
            timeSeries = new TimeSeries[] { countTimeSeries };
            values = new Object[] { 1 };
        }
        // Break down with a value column
        else if (hasBreakDownSetting)
        {
            timeSeries = new TimeSeries[1];
            values = new Object[1];
            // Count
            if (settings.valueColumns.isEmpty())
            {
                values[0] = 1;
            }
        }
        // No break down with value columns
        else
        {
            timeSeries = new TimeSeries[settings.valueColumns.size()];
            values = new Object[timeSeries.length];
        }

        RegularTimePeriod xbucket = null;
        // Extract x bucket
        int size = rowColumns.size();
        for (int i = 0; i < size; i++)
        {
            String column = lowerCase(rowColumns.get(i));
            Object value = rowValues.get(i);

            if (Objects.equals(column, settings.xcolumn))
            {
                xbucket = settings.interval.getBucket(value);
                break;
            }
        }

        // No domain found, return
        if (xbucket == null)
        {
            return;
        }

        // Break down time series
        if (hasBreakDownSetting)
        {
            List<Object> breakDownValue = new ArrayList<>(settings.breakDownColumns.size());
            for (String col : settings.breakDownColumns)
            {
                int index = rowColumns.indexOf(col);
                breakDownValue.add(index >= 0 ? rowValues.get(index)
                        : NULL);
            }

            timeSeries[0] = valueTimeSeries.computeIfAbsent(breakDownValue, v ->
            {
                TimeSeries s = new TimeSeries(String.valueOf(v));
                timeSeriesDataset.addSeries(s);
                return s;
            });
        }

        if (!settings.valueColumns.isEmpty())
        {
            size = settings.valueColumns.size();
            for (int i = 0; i < size; i++)
            {
                int index = rowColumns.indexOf(settings.valueColumns.get(i));
                values[i] = index >= 0 ? rowValues.get(index)
                        : NULL;

                if (!hasBreakDownSetting)
                {
                    timeSeries[i] = valueTimeSeries.get(settings.valueColumns.get(i));
                }
            }
        }

        int length = timeSeries.length;
        for (int i = 0; i < length; i++)
        {
            if (timeSeries[i] == null
                    || values[i] == null)
            {
                continue;
            }

            QTimeSeriesDataItem dataItem = (QTimeSeriesDataItem) timeSeries[i].getDataItem(xbucket);
            if (dataItem == null)
            {
                bucketStartRow.put(xbucket, rowNumber - 1);
                timeSeries[i].add(new QTimeSeriesDataItem(xbucket, (Number) values[i]));
            }
            else
            {
                dataItem.count++;
                timeSeries[i].update(xbucket, settings.function.getValue(settings.valueType, dataItem, (Number) values[i]));
            }
        }

        lastNotifyRowNumber++;
        if (lastNotifyRowNumber > 100)

        {
            notifyChart();
        }
    }

    private void populateCategoryChart(int rowNumber, List<String> rowColumns, List<Object> rowValues)
    {
        Comparable<?> categoryRowValue = null;

        // First find category row value
        int size = rowColumns.size();
        for (int i = 0; i < size; i++)
        {
            String column = lowerCase(rowColumns.get(i));
            Object rowValue = rowValues.get(i);
            if (categoryRowValue == null
                    && rowValue != null
                    && Objects.equals(settings.xcolumn, column))
            {
                categoryRowValue = (Comparable<?>) rowValue;
                break;
            }
        }

        // No row value found, nothing to draw
        if (categoryRowValue == null)
        {
            return;
        }

        boolean hasBreakDownSetting = !settings.breakDownColumns.isEmpty();
        List<String> categoryColumns;
        java.util.function.Function<String, Number> valueSupplier;
        // Count mode
        if (!hasBreakDownSetting
                && settings.valueColumns.isEmpty())
        {
            categoryColumns = singletonList("count");
            valueSupplier = col -> 1;
        }
        // Break down mode
        else if (hasBreakDownSetting)
        {
            List<Object> breakDownValue = new ArrayList<>(settings.breakDownColumns.size());
            for (String col : settings.breakDownColumns)
            {
                int index = rowColumns.indexOf(col);
                breakDownValue.add(index >= 0 ? rowValues.get(index)
                        : NULL);
            }

            categoryColumns = singletonList(String.valueOf(breakDownValue));
            Object value = 1;
            if (!settings.valueColumns.isEmpty())
            {
                int index = rowColumns.indexOf(settings.valueColumns.get(0));
                value = index >= 0 ? rowValues.get(index)
                        : NULL;
            }
            final Object val = value;
            valueSupplier = col -> (Number) val;
        }
        // Value mode
        else
        {
            categoryColumns = settings.valueColumns;
            valueSupplier = col ->
            {
                int index = rowColumns.indexOf(col);
                if (index < 0)
                {
                    return 0;
                }
                return (Number) rowValues.get(index);
            };
        }

        int rowIndex = categoryDataset.getRowIndex(categoryRowValue);
        for (String col : categoryColumns)
        {
            int columnIndex = categoryDataset.getColumnIndex(col);

            QNumber existingValue;
            Number newValue = valueSupplier.apply(col);
            if (rowIndex >= 0
                    && columnIndex >= 0)
            {
                existingValue = (QNumber) categoryDataset.getValue(rowIndex, columnIndex);
                if (existingValue == null)
                {
                    existingValue = new QNumber();
                }
            }
            else
            {
                existingValue = new QNumber();
            }
            existingValue.count++;

            newValue = settings.function.getValue(settings.valueType, existingValue, newValue);
            existingValue.setValue(newValue);
            categoryDataset.setValue(existingValue, categoryRowValue, col);
        }

        lastNotifyRowNumber++;
        if (lastNotifyRowNumber > 100)

        {
            notifyChart();
        }
    }

    private void notifyChart()
    {
        lastNotifyRowNumber = 0;
        SwingUtilities.invokeLater(() ->
        {
            if (settings.xisTimeSeries)
            {
                timeSeriesDataset.notifyEx();
            }
            else
            {
                categoryDataset.notifyEx();
            }
        });
    }

    /**
     * Settings for a bar graph
     */
    private record GraphSettings(GraphType graphType, String xcolumn, boolean xisTimeSeries, Interval interval, List<String> valueColumns, List<String> breakDownColumns, ValueType valueType,
            Function function)
    {
    }

    private enum GraphType
    {
        BAR,
        LINE
    }

    enum ValueType
    {
        INTEGER(NumberAxis.createIntegerTickUnits()),
        DECIMAL(new NumberTickUnitSource(false));

        private final TickUnitSource tickUnit;

        private ValueType(TickUnitSource tickUnit)
        {
            this.tickUnit = tickUnit;
        }

    }

    enum Function
    {
        SUM
        {
            @Override
            Number getValue(ValueType valueType, DataItem dataItem, Number newValue)
            {
                Number accumulated = dataItem.getValue();
                if (valueType == ValueType.DECIMAL)
                {
                    return accumulated.doubleValue() + newValue.doubleValue();
                }
                return accumulated.intValue() + newValue.intValue();
            }
        },
        AVG
        {
            @Override
            Number getValue(ValueType valueType, DataItem dataItem, Number newValue)
            {
                if (valueType == ValueType.DECIMAL)
                {
                    dataItem.setSum(dataItem.getSum()
                            .doubleValue() + newValue.doubleValue());
                    return dataItem.getSum()
                            .doubleValue() / dataItem.getCount();
                }
                dataItem.setSum(dataItem.getSum()
                        .intValue() + newValue.intValue());
                return dataItem.getSum()
                        .intValue() / dataItem.getCount();
            }
        },
        MIN
        {
            @Override
            Number getValue(ValueType valueType, DataItem dataItem, Number newValue)
            {
                Number accumulated = dataItem.getValue();
                if (valueType == ValueType.DECIMAL)
                {
                    return accumulated.doubleValue() < newValue.doubleValue() ? accumulated
                            : newValue;
                }
                return accumulated.intValue() < newValue.intValue() ? accumulated
                        : newValue;
            }
        },
        MAX
        {
            @Override
            Number getValue(ValueType valueType, DataItem dataItem, Number newValue)
            {
                Number accumulated = dataItem.getValue();
                if (valueType == ValueType.DECIMAL)
                {
                    return accumulated.doubleValue() < newValue.doubleValue() ? newValue
                            : accumulated;
                }
                return accumulated.intValue() < newValue.intValue() ? newValue
                        : accumulated;
            }
        };

        abstract Number getValue(ValueType valueType, DataItem dataItem, Number newValue);
    }

    enum Interval
    {
        MINUTE(new DateTickUnit(DateTickUnitType.MINUTE, 1), new SimpleDateFormat("m:00"))
        {
            @Override
            RegularTimePeriod getBucket(Object value)
            {
                ZonedDateTime zd = convert(value);
                return new Minute(Date.from(ZonedDateTime.of(zd.getYear(), zd.getMonthValue(), zd.getDayOfMonth(), zd.getHour(), zd.getMinute(), 0, 0, zd.getZone())
                        .toInstant()));
            }
        },
        HOUR(new DateTickUnit(DateTickUnitType.HOUR, 1), new SimpleDateFormat("H:00"))
        {
            @Override
            RegularTimePeriod getBucket(Object value)
            {
                ZonedDateTime zd = convert(value);
                return new Hour(Date.from(ZonedDateTime.of(zd.getYear(), zd.getMonthValue(), zd.getDayOfMonth(), zd.getHour(), 0, 0, 0, zd.getZone())
                        .toInstant()));
            }
        },
        DAY(new DateTickUnit(DateTickUnitType.DAY, 1), new SimpleDateFormat("M-d"))
        {
            @Override
            RegularTimePeriod getBucket(Object value)
            {
                ZonedDateTime zd = convert(value);
                return new Day(Date.from(ZonedDateTime.of(zd.getYear(), zd.getMonthValue(), zd.getDayOfMonth(), 0, 0, 0, 0, zd.getZone())
                        .toInstant()));
            }
        };

        private final DateTickUnit tickUnit;
        private final DateFormat tickDateFormat;

        private Interval(DateTickUnit tickUnit, DateFormat tickDateFormat)
        {
            this.tickUnit = tickUnit;
            this.tickDateFormat = tickDateFormat;
        }

        abstract RegularTimePeriod getBucket(Object value);

        private static ZonedDateTime convert(Object value)
        {
            if (value instanceof ZonedDateTime zd)
            {
                return zd;
            }
            else if (value instanceof LocalDateTime ldt)
            {
                return ldt.atZone(ZoneId.systemDefault());
            }
            else if (value instanceof LocalDate ld)
            {
                return ld.atStartOfDay(ZoneId.systemDefault());
            }
            else if (value instanceof UTF8String s)
            {
                return ZonedDateTime.parse(s.toString());
            }
            else if (value instanceof String s)
            {
                return ZonedDateTime.parse(s);
            }
            else if (value instanceof EpochDateTime dt)
            {
                return dt.getLocalDateTime()
                        .atZone(ZoneId.systemDefault());
            }
            else if (value instanceof EpochDateTimeOffset dto)
            {
                return dto.getZonedDateTime();
            }
            throw new IllegalArgumentException("Cannot convert " + value + " to datetime");
        }
    }
}
