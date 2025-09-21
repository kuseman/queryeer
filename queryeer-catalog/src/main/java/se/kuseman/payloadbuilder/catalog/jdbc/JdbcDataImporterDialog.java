package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DurationFormatUtils;

import com.queryeer.api.component.DialogUtils.ADialog;
import com.queryeer.api.service.IPayloadbuilderService;
import com.queryeer.api.utils.StringUtils;

import se.kuseman.payloadbuilder.api.catalog.Column;
import se.kuseman.payloadbuilder.api.catalog.ResolvedType;
import se.kuseman.payloadbuilder.api.catalog.Schema;
import se.kuseman.payloadbuilder.api.execution.Decimal;
import se.kuseman.payloadbuilder.api.execution.EpochDateTime;
import se.kuseman.payloadbuilder.api.execution.EpochDateTimeOffset;
import se.kuseman.payloadbuilder.api.execution.TupleVector;
import se.kuseman.payloadbuilder.api.execution.UTF8String;
import se.kuseman.payloadbuilder.api.execution.ValueVector;
import se.kuseman.payloadbuilder.catalog.fs.FilesystemCatalog;
import se.kuseman.payloadbuilder.catalog.jdbc.dialect.JdbcDialect;

/** Data importer for JDBC. */
class JdbcDataImporterDialog extends ADialog
{
    private final IPayloadbuilderService payloadbuilderService;
    private final JdbcConnectionsModel connectionsModel;
    private final JdbcConnection jdbcConnection;
    private final String database;
    private final JdbcDialect dialect;

    private File selectedFile;
    private ImportWorker importWorker;

    // UI
    private static final String PROGRESS_TEXT = "progressText";
    private final JButton btnSelectFile;
    private final JTextField tfSelectedFile;
    private final JComboBox<ImportFileType> cbImportType;
    private final JTextField tfBatchSize;
    private final JTextField tfCsvDelimiter;
    private final JTextField tfJsonPath;
    private final JTextField tfXmlPath;
    private final JTextField tfTableName;
    private final JTextArea taCreateTable;
    private final JTextArea taQuery;
    private final JButton btnImport;
    private final JButton btnCancel;
    private final JButton btnGenerateCreateTable;
    private final JButton btnReset;
    private final JTextField tfProgress;
    private final JTabbedPane tabbedPane;

    JdbcDataImporterDialog(IPayloadbuilderService payloadbuilderService, JdbcConnectionsModel connectionsModel, JdbcConnection jdbcConnection, String database, JdbcDialect dialect)
    {
        this.payloadbuilderService = payloadbuilderService;
        this.connectionsModel = connectionsModel;
        this.jdbcConnection = jdbcConnection;
        this.database = database;
        this.dialect = dialect;

        setTitle("Import Data To: " + jdbcConnection.getName() + "/" + database);
        getContentPane().setLayout(new GridBagLayout());

        // CSOFF
        KeyAdapter generatePlbQueryKeyAdapter = new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                generatePlbQuery(emptyList());
            }
        };
        // CSON

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.gridx = 0;
        gbc.gridy = 0;

        btnSelectFile = new JButton("Select File");
        btnSelectFile.addActionListener(e -> onSelectFile(e));
        getContentPane().add(btnSelectFile, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.gridx = 1;
        gbc.gridwidth = 6;
        gbc.weightx = 1.0;
        tfSelectedFile = new JTextField();
        tfSelectedFile.setEditable(false);
        getContentPane().add(tfSelectedFile, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy = 1;

        getContentPane().add(new JLabel("Type:"), gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        cbImportType = new JComboBox<>();
        cbImportType.setModel(new DefaultComboBoxModel<>(ImportFileType.values()));
        cbImportType.addActionListener(this::onTypeChange);
        getContentPane().add(cbImportType, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;

        getContentPane().add(new JLabel("Batch Size:"), gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.gridx = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        tfBatchSize = new JTextField(5);
        tfBatchSize.setText("250");
        tfBatchSize.addKeyListener(generatePlbQueryKeyAdapter);
        getContentPane().add(tfBatchSize, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.gridx = 4;
        gbc.fill = GridBagConstraints.NONE;

        getContentPane().add(new JLabel("Table Name:"), gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.gridx = 5;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        tfTableName = new JTextField();
        getContentPane().add(tfTableName, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.gridx = 0;
        gbc.gridy = 2;

        getContentPane().add(new JLabel("CSV Delimiter:"), gbc);

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 1;
        gbc.gridy = 2;

        tfCsvDelimiter = new JTextField(5);
        tfCsvDelimiter.setText(";");
        tfCsvDelimiter.setEnabled(false);
        tfCsvDelimiter.addKeyListener(generatePlbQueryKeyAdapter);
        getContentPane().add(tfCsvDelimiter, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 0);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 3;
        gbc.gridy = 2;

        getContentPane().add(new JLabel("JSON Path:"), gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 4;
        gbc.gridy = 2;
        gbc.weightx = 0.4;

        tfJsonPath = new JTextField();
        tfJsonPath.setEnabled(false);
        tfJsonPath.addKeyListener(generatePlbQueryKeyAdapter);
        getContentPane().add(tfJsonPath, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 5;
        gbc.gridy = 2;

        getContentPane().add(new JLabel("XML Path:"), gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 6;
        gbc.gridy = 2;
        gbc.weightx = 0.4;

        tfXmlPath = new JTextField();
        tfXmlPath.setEnabled(false);
        tfXmlPath.addKeyListener(generatePlbQueryKeyAdapter);
        getContentPane().add(tfXmlPath, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 0;
        gbc.gridy = 3;

        btnGenerateCreateTable = new JButton("Generate Create Table");
        btnGenerateCreateTable.setEnabled(false);
        btnGenerateCreateTable.addActionListener(this::onGenerateCreateTable);
        getContentPane().add(btnGenerateCreateTable, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 1;
        gbc.gridy = 3;

        btnReset = new JButton("Reset");
        btnReset.addActionListener(this::onReset);
        getContentPane().add(btnReset, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 2;
        gbc.gridy = 3;

        btnImport = new JButton("Import");
        btnImport.setEnabled(false);
        btnImport.addActionListener(this::onImport);
        getContentPane().add(btnImport, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 3;
        gbc.gridy = 3;

        btnCancel = new JButton("Cancel Import");
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(l -> importWorker.abort());
        getContentPane().add(btnCancel, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 0, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 4;
        gbc.gridy = 3;
        gbc.gridwidth = 3;

        tfProgress = new JTextField();
        tfProgress.setEnabled(false);
        tfProgress.setText("");
        getContentPane().add(tfProgress, gbc);

        tabbedPane = new JTabbedPane();

        taCreateTable = new JTextArea();
        tabbedPane.addTab("Create Table", new JScrollPane(taCreateTable));

        taQuery = new JTextArea();
        taQuery.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                tabbedPane.setTitleAt(1, "Query (Custom)");
                tfBatchSize.setEnabled(false);
                tfCsvDelimiter.setEnabled(false);
                tfJsonPath.setEnabled(false);
                tfXmlPath.setEnabled(false);
                cbImportType.setEnabled(false);
            }
        });
        tabbedPane.addTab("Query", new JScrollPane(taQuery));

        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 7;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        getContentPane().add(tabbedPane, gbc);

        onTypeChange(null);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 800));
        pack();
        setLocationRelativeTo(null);
        pack();

        setModalityType(ModalityType.APPLICATION_MODAL);
    }

    private void onImport(ActionEvent e)
    {
        // Do some fail safe checks
        if (isBlank(taCreateTable.getText())
                || isBlank(taQuery.getText()))
        {
            return;
        }

        importWorker = new ImportWorker();
        importWorker.addPropertyChangeListener(l ->
        {
            if (PROGRESS_TEXT.equalsIgnoreCase(l.getPropertyName()))
            {
                tfProgress.setText(String.valueOf(l.getNewValue()));
            }
            else if ("state".equalsIgnoreCase(l.getPropertyName())
                    && StateValue.DONE.equals(l.getNewValue()))
            {
                btnSelectFile.setEnabled(true);
                btnReset.setEnabled(true);
                btnGenerateCreateTable.setEnabled(true);
                btnImport.setEnabled(true);
                btnCancel.setEnabled(false);
                taQuery.setEnabled(true);
                taCreateTable.setEnabled(true);
                tfBatchSize.setEditable(true);
                tfTableName.setEnabled(true);
                tfJsonPath.setEditable(true);
                tfXmlPath.setEditable(true);
                tfBatchSize.setEditable(true);
                cbImportType.setEnabled(true);

                if (importWorker.exception != null)
                {
                    StringWriter sw = new StringWriter();
                    importWorker.exception.printStackTrace(new PrintWriter(sw));
                    JOptionPane.showMessageDialog(this, "Error importing: " + System.lineSeparator() + sw.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        tfBatchSize.setEditable(false);
        tfTableName.setEnabled(false);
        tfJsonPath.setEditable(false);
        tfXmlPath.setEditable(false);
        tfBatchSize.setEditable(false);
        cbImportType.setEnabled(false);
        btnSelectFile.setEnabled(false);
        btnReset.setEnabled(false);
        btnGenerateCreateTable.setEnabled(false);
        btnImport.setEnabled(false);
        btnCancel.setEnabled(true);
        taQuery.setEnabled(false);
        taCreateTable.setEnabled(false);

        importWorker.execute();
    }

    private void onReset(ActionEvent e)
    {
        taCreateTable.setText("");
        taQuery.setText("");
        tabbedPane.setTitleAt(1, "Query");
        tfCsvDelimiter.setText(";");
        tfJsonPath.setText("");
        tfXmlPath.setText("");
        tfTableName.setText("");
        tfBatchSize.setEnabled(true);
        tfBatchSize.setText("250");
        tfSelectedFile.setText("");
        selectedFile = null;
        tfProgress.setText("");
        btnImport.setEnabled(false);
        btnCancel.setEnabled(false);
        btnReset.setEnabled(true);
        btnSelectFile.setEnabled(true);
        cbImportType.setEnabled(true);
        cbImportType.setSelectedItem(ImportFileType.CSV);
        onTypeChange(null);
    }

    private void onTypeChange(ActionEvent e)
    {
        tfCsvDelimiter.setEnabled(false);
        tfJsonPath.setEnabled(false);
        tfXmlPath.setEnabled(false);

        ImportFileType type = (ImportFileType) cbImportType.getSelectedItem();
        if (type == ImportFileType.CSV)
        {
            tfCsvDelimiter.setEnabled(true);
        }
        else if (type == ImportFileType.JSON)
        {
            tfJsonPath.setEnabled(true);
        }
        else if (type == ImportFileType.XML)
        {
            tfXmlPath.setEnabled(true);
        }

        generatePlbQuery(emptyList());
    }

    private void onGenerateCreateTable(ActionEvent ae)
    {
        tabbedPane.setSelectedIndex(0);

        if (selectedFile == null)
        {
            JOptionPane.showMessageDialog(this, "Import file is mandatory when importing");
            return;
        }

        ImportFileType type = (ImportFileType) cbImportType.getSelectedItem();
        if (type == ImportFileType.JSON
                && StringUtils.isBlank(tfJsonPath.getText()))
        {
            JOptionPane.showMessageDialog(this, "JsonPath is mandatory when importing JSON files");
            return;
        }
        else if (type == ImportFileType.XML
                && StringUtils.isBlank(tfXmlPath.getText()))
        {
            JOptionPane.showMessageDialog(this, "XmlPath is mandatory when importing XML files");
            return;
        }
        else if (type == ImportFileType.CSV
                && StringUtils.isBlank(tfCsvDelimiter.getText()))
        {
            JOptionPane.showMessageDialog(this, "CSV delimiter is mandatory when importing CSV files");
            return;
        }
        else if (StringUtils.isBlank(tfTableName.getText()))
        {
            JOptionPane.showMessageDialog(this, "Table Name is mandatory");
            return;
        }

        if (isBlank(taQuery.getText()))
        {
            JOptionPane.showMessageDialog(this, "Invalid PLB Query");
            return;
        }

        btnSelectFile.setEnabled(false);
        btnReset.setEnabled(false);
        btnGenerateCreateTable.setEnabled(false);
        taCreateTable.setText("");
        btnImport.setEnabled(false);

        SwingWorker<GenerateCreateTableResult, Void> worker = new SwingWorker<>()
        {
            @Override
            protected GenerateCreateTableResult doInBackground() throws Exception
            {
                firePropertyChange(PROGRESS_TEXT, "", "Compiling Query");

                AtomicReference<GenerateCreateTableResult> result = new AtomicReference<>();
                payloadbuilderService.queryRaw(tv ->
                {
                    try
                    {
                        firePropertyChange(PROGRESS_TEXT, "", "Generating Create Table Statement");
                        try (Connection connection = connectionsModel.createConnection(jdbcConnection))
                        {
                            String createTable = generateCreateTableStatement(connection, tv);
                            result.set(new GenerateCreateTableResult(createTable, null));
                        }
                    }
                    catch (Exception e)
                    {
                        result.set(new GenerateCreateTableResult(null, e));
                    }
                    return false;
                }, taQuery.getText(), Map.of("fs", new FilesystemCatalog()));
                return result.get();
            }

            @Override
            protected void done()
            {
                Exception e = null;
                try
                {
                    GenerateCreateTableResult result = get();
                    if (result != null)
                    {
                        if (result.e != null)
                        {
                            e = result.e;
                        }
                        else
                        {
                            taCreateTable.setText(result.statement);
                            taCreateTable.setCaretPosition(0);
                        }
                    }
                }
                catch (Exception ee)
                {
                    e = ee;
                }

                btnSelectFile.setEnabled(true);
                btnReset.setEnabled(true);
                btnGenerateCreateTable.setEnabled(true);
                if (e != null)
                {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    JOptionPane.showMessageDialog(JdbcDataImporterDialog.this, "Error generating create table statement: " + System.lineSeparator() + sw.toString(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
                else
                {
                    boolean blankCreateTable = isBlank(taCreateTable.getText());
                    if (blankCreateTable)
                    {
                        JOptionPane.showMessageDialog(JdbcDataImporterDialog.this, "No rows returned from PLB query, create table statement could not be generated", "Info",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    btnImport.setEnabled(!blankCreateTable);
                }
            }
        };

        worker.addPropertyChangeListener(l ->
        {
            if (PROGRESS_TEXT.equalsIgnoreCase(l.getPropertyName()))
            {
                tfProgress.setText(String.valueOf(l.getNewValue()));
            }
        });

        worker.execute();
    }

    private record GenerateCreateTableResult(String statement, Exception e)
    {
    }

    private void generatePlbQuery(List<String> columns)
    {
        int batchSize = 250;
        if (!StringUtils.isBlank(tfBatchSize.getText()))
        {
            try
            {
                batchSize = Integer.parseInt(tfBatchSize.getText());
            }
            catch (NumberFormatException ee)
            {
                tfBatchSize.setText(Integer.toString(batchSize));
            }
        }
        taQuery.setText("");
        if (selectedFile != null)
        {
            ImportFileType type = (ImportFileType) cbImportType.getSelectedItem();
            taQuery.setText(type.getPlbQuery(selectedFile, batchSize, tfCsvDelimiter.getText(), tfJsonPath.getText(), tfXmlPath.getText(), columns));
        }
    }

    private String generateCreateTableStatement(Connection connection, TupleVector tupleVector) throws SQLException
    {
        String identifierQuoteString = connection.getMetaData()
                .getIdentifierQuoteString();

        StringBuilder sb = new StringBuilder("DROP TABLE IF EXISTS ").append(identifierQuoteString)
                .append(tfTableName.getText())
                .append(identifierQuoteString)
                .append(System.lineSeparator());

        sb.append("CREATE TABLE ")
                .append(identifierQuoteString)
                .append(tfTableName.getText())
                .append(identifierQuoteString)
                .append(System.lineSeparator())
                .append("(")
                .append(System.lineSeparator());

        List<String> columns = new ArrayList<>();
        int size = tupleVector.getSchema()
                .getSize();
        for (int i = 0; i < size; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append('\t');

            String columnName = tupleVector.getSchema()
                    .getColumns()
                    .get(i)
                    .getName();
            columns.add(columnName);

            ValueVector column = tupleVector.getColumn(i);
            sb.append(getColumnDefinition(columnName, column, identifierQuoteString));
            sb.append(System.lineSeparator());
        }

        SwingUtilities.invokeLater(() -> generatePlbQuery(columns));

        sb.append(")")
                .append(System.lineSeparator());
        return sb.toString();
    }

    /**
     * Returns the column statement for create table. This method tries to infer the data type from the tuple vector.
     */
    private String getColumnDefinition(String columnName, ValueVector column, String identifierQuoteString)
    {
        // TODO: Make use of JdbcDialect to set default
        // Use SQL-server for now
        String columnType = "NVARCHAR(500)";
        ResolvedType type = column.type();

        if (type.getType() == Column.Type.Boolean)
        {
            columnType = "BIT";
        }
        else if (type.getType() == Column.Type.Int)
        {
            columnType = "INT";
        }
        else if (type.getType() == Column.Type.Long)
        {
            columnType = "BIGINT";
        }
        else if (type.getType() == Column.Type.DateTime)
        {
            columnType = "DATETIME";
        }
        else if (type.getType() == Column.Type.DateTimeOffset)
        {
            columnType = "DATETIMEOFFSET";
        }
        else if (type.getType() == Column.Type.Decimal)
        {
            columnType = "NUMERIC(19,4)";
        }
        else if (type.getType() == Column.Type.Float)
        {
            columnType = "REAL";
        }
        else if (type.getType() == Column.Type.Double)
        {
            columnType = "FLOAT";
        }
        // String and Any => use character as default column type
        else if (type.getType() == Column.Type.String
                || type.getType() == Column.Type.Any)
        {
            // Search the first batch and pick the max size
            int size = column.size();
            int columnSize = 1;
            for (int i = 0; i < size; i++)
            {
                if (column.isNull(i))
                {
                    continue;
                }

                // TODO: Try to parse

                columnSize = Math.max(columnSize, column.valueAsString(i)
                        .length());
            }
            // Make the size double from what was found to safe a bit
            columnType = "NVARCHAR(" + columnSize * 2 + ")";
        }
        else if (type.getType()
                .isComplex())
        {
            throw new IllegalArgumentException("Nested types are not supported");
        }

        return "%s%s%s %s".formatted(identifierQuoteString, columnName, identifierQuoteString, columnType);
    }

    private void onSelectFile(ActionEvent e)
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
        fileChooser.setDialogTitle("Select File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("XML Files", "xml"));

        selectedFile = null;
        tfSelectedFile.setText("");
        tfTableName.setText("");
        tfCsvDelimiter.setText(";");
        tfJsonPath.setText("");
        tfXmlPath.setText("");
        taQuery.setText("");
        taCreateTable.setText("");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION)
        {
            selectedFile = fileChooser.getSelectedFile();
            tfSelectedFile.setText(selectedFile.getAbsolutePath());
            ImportFileType importFileType = ImportFileType.from(selectedFile);
            cbImportType.setSelectedItem(importFileType);
            tfTableName.setText("t_" + selectedFile.getName());
            generatePlbQuery(emptyList());
        }

        btnGenerateCreateTable.setEnabled(selectedFile != null);
    }

    /** Swing worker used when importing data. Batch imports from PLB query and publishes progress. */
    private class ImportWorker extends SwingWorker<Void, Void>
    {
        private volatile boolean abort = false;
        private volatile Exception exception;
        private volatile Statement statement;

        @Override
        protected Void doInBackground() throws Exception
        {
            try
            {
                long time = System.nanoTime();
                AtomicInteger batchCount = new AtomicInteger(1);
                AtomicInteger rowCount = new AtomicInteger(0);

                try (Connection connection = connectionsModel.createConnection(jdbcConnection, true))
                {
                    if (dialect.usesSchemaAsDatabase())
                    {
                        connection.setSchema(database);
                    }
                    else
                    {
                        connection.setCatalog(database);
                    }
                    firePropertyChange(PROGRESS_TEXT, "", "Creating Table");

                    try (PreparedStatement stm = connection.prepareStatement(taCreateTable.getText()))
                    {
                        this.statement = stm;
                        stm.execute();
                    }

                    AtomicReference<String> insertStatement = new AtomicReference<>();
                    AtomicReference<Schema> schema = new AtomicReference<>();

                    String identifierQuoteString = connection.getMetaData()
                            .getIdentifierQuoteString();
                    connection.setAutoCommit(false);
                    Function<TupleVector, Boolean> vectorConsumer = tv ->
                    {
                        if (insertStatement.get() == null)
                        {
                            StringBuilder sb = new StringBuilder();
                            // INSERT INTO "t_some_file.csv" ("col1", "col2", "col3") VALUES (?,?,?)
                            sb.append("INSERT INTO %s%s%s (".formatted(identifierQuoteString, tfTableName.getText(), identifierQuoteString));
                            schema.set(tv.getSchema());
                            boolean first = true;
                            for (Column column : schema.get()
                                    .getColumns())
                            {
                                if (!first)
                                {
                                    sb.append(',');
                                }

                                sb.append("%s%s%s".formatted(identifierQuoteString, column.getName(), identifierQuoteString));
                                first = false;
                            }
                            sb.append(") VALUES (")
                                    .append(IntStream.range(0, schema.get()
                                            .getSize())
                                            .mapToObj(i -> "?")
                                            .collect(joining(",")))
                                    .append(')');

                            insertStatement.set(sb.toString());
                        }

                        long timeSoFar = TimeUnit.MILLISECONDS.convert(System.nanoTime() - time, TimeUnit.NANOSECONDS);
                        firePropertyChange(PROGRESS_TEXT, "",
                                "Inserting Batch " + batchCount.get() + ", Inserted Rows: " + rowCount.get() + ", Time: " + DurationFormatUtils.formatDurationHMS(timeSoFar));

                        try (PreparedStatement stm = connection.prepareStatement(insertStatement.get()))
                        {
                            this.statement = stm;

                            List<Column> currentVectorColumns = tv.getSchema()
                                    .getColumns();

                            int schemaSize = schema.get()
                                    .getSize();
                            int size = tv.getRowCount();
                            for (int i = 0; i < size; i++)
                            {
                                for (int j = 0; j < schemaSize; j++)
                                {
                                    if (abort)
                                    {
                                        break;
                                    }

                                    ValueVector column = null;
                                    // Find the ordinal of the column
                                    for (int k = j; k < currentVectorColumns.size(); k++)
                                    {
                                        if (schema.get()
                                                .getColumns()
                                                .get(j)
                                                .getName()
                                                .equals(currentVectorColumns.get(k)
                                                        .getName()))
                                        {
                                            column = tv.getColumn(k);
                                            break;
                                        }
                                    }

                                    // No matching column => skip
                                    if (column == null)
                                    {
                                        stm.setObject(j + 1, null);
                                        continue;
                                    }

                                    Object value = column.valueAsObject(i);
                                    // Unwrap PLB-types
                                    if (value instanceof UTF8String s)
                                    {
                                        value = s.toString();
                                    }
                                    else if (value instanceof Decimal d)
                                    {
                                        value = d.asBigDecimal();
                                    }
                                    else if (value instanceof EpochDateTime edt)
                                    {
                                        value = edt.getLocalDateTime();
                                    }
                                    else if (value instanceof EpochDateTimeOffset edto)
                                    {
                                        value = edto.getZonedDateTime();
                                    }

                                    stm.setObject(j + 1, value);
                                }
                                rowCount.incrementAndGet();
                                stm.addBatch();
                            }
                            stm.executeBatch();
                            connection.commit();
                        }
                        catch (Exception e)
                        {
                            // Don't show error message upon abortion
                            if (!abort)
                            {
                                exception = e;
                            }
                            return false;
                        }
                        finally
                        {
                            batchCount.incrementAndGet();
                        }
                        return true;
                    };

                    firePropertyChange(PROGRESS_TEXT, "", "Running PLB Query");
                    payloadbuilderService.queryRaw(vectorConsumer, taQuery.getText(), Map.of("fs", new FilesystemCatalog()));
                }

                long totalTime = TimeUnit.MILLISECONDS.convert(System.nanoTime() - time, TimeUnit.NANOSECONDS);
                firePropertyChange(PROGRESS_TEXT, "", "Inserted " + batchCount.get() + " batches, " + rowCount.get() + " rows, in: " + DurationFormatUtils.formatDurationHMS(totalTime));
            }
            catch (Exception e)
            {
                exception = e;
            }
            return null;
        }

        private void abort()
        {
            abort = true;
            Statement stm = this.statement;
            try
            {
                if (stm != null)
                {
                    stm.cancel();
                }
            }
            catch (SQLException e)
            {
                // Swallow
            }
        }
    }

    /** Type of import. */
    private enum ImportFileType
    {
        CSV("OPENCSV"),
        JSON("OPENJSON"),
        XML("OPENXML");

        private final String tableFunction;

        ImportFileType(String tableFunction)
        {
            this.tableFunction = requireNonNull(tableFunction);
        }

        /** Returns the default PLB query for this import */
        String getPlbQuery(File file, int batchSize, String csvDelimiter, String jsonPath, String xmlPath, List<String> columns)
        {
            String tableOptions = getTableOptions(csvDelimiter, jsonPath, xmlPath);
            return """
                    SELECT %s
                    FROM %s(fs#contents('%s')) x%sWITH (batch_size = %d%s)
                    """.formatted(columns.isEmpty() ? "*"
                    : columns.stream()
                            .map(c -> "\"" + c + "\"")
                            .collect(joining(",")),
                    tableFunction, file.getAbsolutePath()
                            .replace('\\', '/'),
                    System.lineSeparator(), batchSize, isBlank(tableOptions) ? ""
                            : ", " + tableOptions);
        }

        private String getTableOptions(String csvDelimiter, String jsonPath, String xmlPath)
        {
            if (this == CSV)
            {
                if (!StringUtils.isBlank(csvDelimiter))
                {
                    return "columnSeparator = '%s'".formatted(csvDelimiter);
                }
            }
            else if (this == JSON)
            {
                if (!StringUtils.isBlank(jsonPath))
                {
                    return "jsonPath = '%s'".formatted(jsonPath);
                }
            }
            else if (this == XML)
            {
                if (!StringUtils.isBlank(xmlPath))
                {
                    return "xmlPath = '%s'".formatted(xmlPath);
                }
            }
            return "";
        }

        /** Try to match import type from file */
        static ImportFileType from(File file)
        {
            if (Strings.CI.endsWith(file.getName(), ".csv"))
            {
                return CSV;
            }
            else if (Strings.CI.endsWith(file.getName(), ".json"))
            {
                return JSON;
            }
            else if (Strings.CI.endsWith(file.getName(), ".xml"))
            {
                return XML;
            }

            // Fallback to CSV
            return CSV;
        }
    }
}
