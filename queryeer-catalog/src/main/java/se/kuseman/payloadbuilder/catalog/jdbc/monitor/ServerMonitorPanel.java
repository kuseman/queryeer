package se.kuseman.payloadbuilder.catalog.jdbc.monitor;

import static java.util.Objects.requireNonNull;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IIconFactory.Provider;

import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionContext;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** Panel that displays live server monitoring data (sessions, waits, locks, etc.) */
public class ServerMonitorPanel extends JPanel
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMonitorPanel.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int DEFAULT_INTERVAL_SECONDS = 5;

    private static final ExecutorService MONITOR_EXECUTOR = Executors.newCachedThreadPool(BasicThreadFactory.builder()
            .daemon(true)
            .namingPattern("ServerMonitor#-%d")
            .build());

    private final IConnectionContext connectionContext;
    private final IDialogFactory dialogFactory;

    // UI
    private final JTabbedPane tabbedPane;
    private final JLabel statusLabel;
    private final JCheckBox autoRefreshCheckBox;
    private final JSpinner intervalSpinner;
    private final JButton refreshButton;
    private final List<SectionTab> sectionTabs = new ArrayList<>();

    // Timer for auto-refresh
    private javax.swing.Timer refreshTimer;

    // Separate monitoring connection
    private volatile Connection monitorConnection;

    public ServerMonitorPanel(IConnectionContext connectionContext, IIconFactory iconFactory, IDialogFactory dialogFactory)
    {
        this.connectionContext = requireNonNull(connectionContext, "connectionContext");
        this.dialogFactory = requireNonNull(dialogFactory, "dialogFactory");
        requireNonNull(iconFactory, "iconFactory");

        setLayout(new BorderLayout());

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, getBackground().darker()));

        refreshButton = new JButton(iconFactory.getIcon(Provider.FONTAWESOME, "REFRESH"));
        refreshButton.setToolTipText("Refresh now");
        refreshButton.addActionListener(e -> triggerRefresh());

        autoRefreshCheckBox = new JCheckBox("Auto-refresh");
        autoRefreshCheckBox.setSelected(false);
        autoRefreshCheckBox.addActionListener(e -> updateTimer());

        intervalSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_INTERVAL_SECONDS, 1, 300, 1));
        intervalSpinner.setPreferredSize(new Dimension(55, intervalSpinner.getPreferredSize().height));
        intervalSpinner.setToolTipText("Refresh interval in seconds");
        ((JSpinner.DefaultEditor) intervalSpinner.getEditor()).getTextField()
                .setEditable(false);
        intervalSpinner.addChangeListener(e -> updateTimer());

        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        toolbar.add(refreshButton);
        toolbar.add(autoRefreshCheckBox);
        toolbar.add(intervalSpinner);
        toolbar.add(new JLabel("s"));
        toolbar.add(statusLabel);

        add(toolbar, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        buildSectionTabs();
    }

    /** Stop timer and close the monitoring connection. Call when the dialog is closed. */
    public void dispose()
    {
        stopTimer();
        closeMonitorConnection();
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void buildSectionTabs()
    {
        tabbedPane.removeAll();
        sectionTabs.clear();

        IServerMonitorExtension monitor = getMonitor();
        if (monitor == null)
        {
            refreshButton.setEnabled(false);
            tabbedPane.addTab("Not supported", new JLabel("Server monitoring is not supported for this connection type.", JLabel.CENTER));
            return;
        }
        refreshButton.setEnabled(true);

        for (ServerMonitorSection section : monitor.getSections())
        {
            DefaultTableModel model = new DefaultTableModel()
            {
                @Override
                public boolean isCellEditable(int row, int column)
                {
                    return false;
                }
            };
            JTable table = new JTable(model);
            table.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getClickCount() == 2
                            && e.getButton() == MouseEvent.BUTTON1)
                    {
                        Point point = e.getPoint();
                        int row = table.rowAtPoint(point);
                        int col = table.columnAtPoint(point);
                        if (row >= 0)
                        {
                            Object value = table.getValueAt(row, col);
                            dialogFactory.showValueDialog("Value viewer - " + table.getColumnName(col), value, IDialogFactory.Format.UNKOWN);
                        }
                    }
                }
            });
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setAutoCreateRowSorter(true);
            table.getTableHeader()
                    .setReorderingAllowed(false);
            table.setFillsViewportHeight(true);

            JScrollPane scroll = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            tabbedPane.addTab(section.name(), scroll);
            tabbedPane.setToolTipTextAt(tabbedPane.getTabCount() - 1, section.description());
            sectionTabs.add(new SectionTab(section, table, model));
        }
    }

    private IServerMonitorExtension getMonitor()
    {
        return Optional.ofNullable(connectionContext.getJdbcDialect())
                .map(JdbcDialect::getMonitorExtension)
                .orElse(null);
    }

    private void triggerRefresh()
    {
        if (sectionTabs.isEmpty())
        {
            return;
        }

        refreshButton.setEnabled(false);
        statusLabel.setText("Refreshing...");

        List<SectionTab> tabsSnapshot = new ArrayList<>(sectionTabs);

        MONITOR_EXECUTOR.submit(() ->
        {
            List<SectionResult> results = new ArrayList<>();
            String errorMsg = null;

            try
            {
                Connection con = getOrCreateConnection();
                for (SectionTab tab : tabsSnapshot)
                {
                    try (Statement stm = con.createStatement(); ResultSet rs = stm.executeQuery(tab.section.query()))
                    {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        String[] columns = new String[colCount];
                        for (int i = 0; i < colCount; i++)
                        {
                            columns[i] = meta.getColumnLabel(i + 1);
                        }

                        List<Object[]> rows = new ArrayList<>();
                        while (rs.next())
                        {
                            Object[] row = new Object[colCount];
                            for (int i = 0; i < colCount; i++)
                            {
                                row[i] = rs.getObject(i + 1);
                            }
                            rows.add(row);
                        }
                        results.add(new SectionResult(tab, columns, rows));
                    }
                    catch (Exception sectionEx)
                    {
                        LOGGER.warn("Error querying section '{}': {}", tab.section.name(), sectionEx.getMessage());
                        results.add(new SectionResult(tab, null, null));
                    }
                }
            }
            catch (Exception e)
            {
                LOGGER.warn("Error refreshing server monitor", e);
                errorMsg = e.getMessage();
                closeMonitorConnection();
            }

            final String finalError = errorMsg;
            final List<SectionResult> finalResults = results;
            SwingUtilities.invokeLater(() ->
            {
                for (SectionResult r : finalResults)
                {
                    if (r.columns != null)
                    {
                        r.tab.updateData(r.columns, r.rows);
                    }
                }
                if (finalError != null)
                {
                    statusLabel.setText("Error: " + truncate(finalError, 80));
                }
                else
                {
                    statusLabel.setText("Last refreshed: " + LocalTime.now()
                            .format(TIME_FMT));
                }
                refreshButton.setEnabled(true);
            });
        });
    }

    private Connection getOrCreateConnection() throws Exception
    {
        Connection con = monitorConnection;
        if (con != null)
        {
            try
            {
                if (!con.isClosed()
                        && con.isValid(2))
                {
                    return con;
                }
            }
            catch (Exception e)
            {
                // fall through to create new
            }
            closeMonitorConnection();
        }

        con = connectionContext.createConnection();
        monitorConnection = con;
        return con;
    }

    private void closeMonitorConnection()
    {
        Connection con = monitorConnection;
        monitorConnection = null;
        if (con != null)
        {
            try
            {
                con.close();
            }
            catch (Exception e)
            {
                // swallow
            }
        }
    }

    private void updateTimer()
    {
        stopTimer();
        if (autoRefreshCheckBox.isSelected())
        {
            int intervalMs = ((Number) intervalSpinner.getValue()).intValue() * 1000;
            refreshTimer = new javax.swing.Timer(intervalMs, e -> triggerRefresh());
            refreshTimer.setInitialDelay(intervalMs);
            refreshTimer.start();
        }
    }

    private void stopTimer()
    {
        if (refreshTimer != null)
        {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    private static String truncate(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..."
                : s;
    }

    // -----------------------------------------------------------------------
    // Inner classes
    // -----------------------------------------------------------------------

    private static class SectionTab
    {
        final ServerMonitorSection section;
        final JTable table;
        final DefaultTableModel model;

        SectionTab(ServerMonitorSection section, JTable table, DefaultTableModel model)
        {
            this.section = section;
            this.table = table;
            this.model = model;
        }

        void updateData(String[] columns, List<Object[]> rows)
        {
            List<? extends SortKey> sortKeys = table.getRowSorter()
                    .getSortKeys();

            Object[][] data = rows.toArray(new Object[0][]);
            model.setDataVector(data, columns);

            table.getRowSorter()
                    .setSortKeys(sortKeys);

            // Auto-size columns
            for (int col = 0; col < table.getColumnCount(); col++)
            {
                int width = 80;
                Component header = table.getTableHeader()
                        .getDefaultRenderer()
                        .getTableCellRendererComponent(table, table.getColumnName(col), false, false, -1, col);
                width = Math.max(width, header.getPreferredSize().width + 10);
                int maxRow = Math.min(table.getRowCount(), 50);
                for (int row = 0; row < maxRow; row++)
                {
                    Component cell = table.getDefaultRenderer(Object.class)
                            .getTableCellRendererComponent(table, table.getValueAt(row, col), false, false, row, col);
                    width = Math.max(width, cell.getPreferredSize().width + 10);
                }
                table.getColumnModel()
                        .getColumn(col)
                        .setPreferredWidth(Math.min(width, 300));
            }
        }
    }

    private record SectionResult(SectionTab tab, String[] columns, List<Object[]> rows)
    {
    }
}
