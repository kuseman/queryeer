package com.queryeer;

import static java.util.Collections.emptySet;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;

import com.queryeer.QueryFileModel.State;
import com.queryeer.api.extensions.catalog.ICatalogExtension;
import com.queryeer.api.extensions.catalog.ICatalogExtension.ExceptionAction;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.api.catalog.CatalogException;
import se.kuseman.payloadbuilder.core.CompiledQuery;
import se.kuseman.payloadbuilder.core.Payloadbuilder;
import se.kuseman.payloadbuilder.core.QueryResult;
import se.kuseman.payloadbuilder.core.parser.AExpressionVisitor;
import se.kuseman.payloadbuilder.core.parser.AStatementVisitor;
import se.kuseman.payloadbuilder.core.parser.Expression;
import se.kuseman.payloadbuilder.core.parser.ParseException;
import se.kuseman.payloadbuilder.core.parser.QueryParser;
import se.kuseman.payloadbuilder.core.parser.QueryStatement;
import se.kuseman.payloadbuilder.core.parser.VariableExpression;

/** Class the executes queries etc. */
class PayloadbuilderService
{
    // private static final Runnable NO_OP = () ->
    // {
    // };
    // private static final int FILE_WRITER_BUFFER_SIZE = 4096;
    private static final VariableVisitor VARIABLES_VISITOR = new VariableVisitor();
    private static final QueryParser PARSER = new QueryParser();
    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime()
            .availableProcessors() * 2, r ->
            {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("QueryExecutor-#" + THREAD_ID.getAndIncrement());
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });

    /** Execute query for provider query file */
    // CSOFF
    static void executeQuery(
            // CSON
            Config config, QueryFileView queryFile, String queryString, Runnable queryFinnishedCallback)
    {
        // MutableObject<String> outputFileName = new MutableObject<>();
        // if (file.getOutput() == Output.FILE)
        // {
        // String fileName = getOutputFileName();
        // // Abort
        // if (fileName == null)
        // {
        // return;
        // }
        // outputFileName.setValue(fileName);
        // }

        final QueryFileModel file = queryFile.getFile();

        EXECUTOR.execute(() ->
        {
            boolean completed = false;
            while (!completed)
            {
                int queryId = file.incrementAndGetQueryId();
                file.setState(State.EXECUTING);

                OutputWriter writer = file.getOutputExtension()
                        .createOutputWriter(queryFile);
                try
                {
                    file.getQuerySession()
                            .setAbortSupplier(() -> file.getState() == State.ABORTED);
                    CompiledQuery query = Payloadbuilder.compile(queryString);
                    QueryResult queryResult = query.execute(file.getQuerySession());

                    while (queryResult.hasMoreResults())
                    {
                        if (file.getState() == State.ABORTED)
                        {
                            break;
                        }

                        queryResult.writeResult(writer);
                        // Flush after each result set
                        writer.flush();
                    }

                    if (file.getState() == State.EXECUTING)
                    {
                        file.setState(State.COMPLETED);
                    }

                    completed = true;
                }
                catch (ParseException e)
                {
                    file.setError(String.format("Syntax error. Line: %d, Column: %d. %s", e.getLine(), e.getColumn(), e.getMessage()));
                    file.setParseErrorLocation(Pair.of(e.getLine(), e.getColumn() + 1));
                    file.setState(State.ERROR);
                    completed = true;
                }
                catch (Exception e)
                {
                    if (e instanceof CatalogException)
                    {
                        CatalogException ce = (CatalogException) e;
                        Optional<ICatalogExtension> catalogExtension = file.getCatalogExtensions()
                                .stream()
                                .filter(kv -> Objects.equals(ce.getCatalogAlias(), kv.getValue()
                                        .getAlias()))
                                .map(kv -> kv.getKey())
                                .findFirst();

                        if (catalogExtension.isPresent()
                                && catalogExtension.get()
                                        .handleException(file.getQuerySession(), ce) == ExceptionAction.RERUN)
                        {
                            // Re-run query
                            continue;
                        }
                    }

                    // Only set error messages if this is the latest query made
                    if (queryId == file.getQueryId())
                    {
                        String message = e.getMessage();
                        if (e.getCause() != null)
                        {
                            message += " (" + e.getCause()
                                    .getClass()
                                    .getSimpleName() + ")";
                        }
                        file.setError(message);
                        file.setState(State.ERROR);
                        if (System.getProperty("devEnv") != null)
                        {
                            e.printStackTrace();
                        }
                    }

                    completed = true;
                }
                finally
                {
                    if (writer != null)
                    {
                        writer.close();
                    }

                    if (queryId == file.getQueryId())
                    {
                        if (file.getState() == State.EXECUTING)
                        {
                            file.setState(State.COMPLETED);
                        }
                        queryFinnishedCallback.run();
                    }
                }
            }
        });
    }

    // /** Extended interface for queryeer output writers */
    // private static class QueryeerOutputWriter
    // {
    // private final OutputWriter writer;
    // private final Runnable newResultSet;
    // private final Runnable resultSetComplete;
    // private final Runnable complete;
    //
    // QueryeerOutputWriter(OutputWriter writer, Runnable newResultSet, Runnable resultSetComplete, Runnable complete)
    // {
    // this.writer = writer;
    // this.newResultSet = newResultSet;
    // this.resultSetComplete = resultSetComplete;
    // this.complete = complete;
    // }
    // }
    //
    // private static QueryeerOutputWriter getOutputWriter(Config config, QueryFileModel file, String outputFileName)
    // {
    // if (file.getOutput() == Output.TABLE)
    // {
    // final TableOutputWriter writer = new TableOutputWriter();
    // return new QueryeerOutputWriter(writer, () ->
    // {
    // // Create new model
    // ResultModel model = new ResultModel(file);
    // writer.setResultModel(model);
    // file.addResult(model);
    // }, () ->
    // {
    // // Mark current model as done and print result
    // writer.resultModel.done();
    // printRowCountResult(file, writer.resultModel.getActualRowCount());
    // }, NO_OP);
    // }
    // else if (file.getOutput() == Output.NONE)
    // {
    // final NoneOutputWriter writer = new NoneOutputWriter(file);
    // return new QueryeerOutputWriter(writer, () -> writer.rowCount = 0, () -> printRowCountResult(file, writer.rowCount), () ->
    // {
    // });
    // }
    // else if (file.getOutput() == Output.TEXT
    // || file.getOutput() == Output.FILE)
    // {
    // Writer output = file.getQuerySession()
    // .getPrintWriter();
    // final MutableObject<CountingOutputStream> countingOutputStream = new MutableObject<>();
    // final MutableObject<OutputWriter> writer = new MutableObject<>();
    //
    // // Redirect output to chosen file
    // if (file.getOutput() == Output.FILE)
    // {
    // try
    // {
    // countingOutputStream.setValue(new CountingOutputStream(new FileOutputStream(new File(outputFileName))));
    //
    // output = new BufferedWriter(new OutputStreamWriter(countingOutputStream.getValue(), StandardCharsets.UTF_8), FILE_WRITER_BUFFER_SIZE);
    // }
    // catch (IOException e)
    // {
    // throw new RuntimeException("Error creating writer for output " + outputFileName);
    // }
    // }
    //
    // if (file.getFormat() == Format.CSV)
    // {
    // writer.setValue(new CsvOutputWriter(output, config.getOutputConfig()
    // .getCsvSettings())
    // {
    // @Override
    // public void endRow()
    // {
    // super.endRow();
    // file.incrementTotalRowCount();
    // }
    // });
    // }
    // else if (file.getFormat() == Format.JSON)
    // {
    // writer.setValue(new JsonOutputWriter(output, config.getOutputConfig()
    // .getJsonSettings())
    // {
    // @Override
    // public void endRow()
    // {
    // super.endRow();
    // file.incrementTotalRowCount();
    // }
    // });
    // }
    //
    // if (writer.getValue() == null)
    // {
    // throw new RuntimeException("Unsupported format " + file.getFormat());
    // }
    //
    // return new QueryeerOutputWriter(writer.getValue(), NO_OP, NO_OP, () ->
    // {
    // // CSOFF
    // if (countingOutputStream.getValue() != null)
    // // CSON
    // {
    // printBytesWritten(file, countingOutputStream.getValue()
    // .getByteCount(), outputFileName);
    // }
    // });
    // }
    //
    // throw new RuntimeException("Unsupported output " + file.getOutput());
    // }
    //
    // private static String getOutputFileName()
    // {
    // // CSOFF
    // JFileChooser fileChooser = new JFileChooser()
    // // CSON
    // {
    // @Override
    // public void approveSelection()
    // {
    // File f = getSelectedFile();
    // if (f.exists()
    // && getDialogType() == SAVE_DIALOG)
    // {
    // int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file", JOptionPane.YES_NO_CANCEL_OPTION);
    // // CSOFF
    // switch (result)
    // // CSON
    // {
    // case JOptionPane.YES_OPTION:
    // super.approveSelection();
    // return;
    // case JOptionPane.NO_OPTION:
    // return;
    // case JOptionPane.CLOSED_OPTION:
    // return;
    // case JOptionPane.CANCEL_OPTION:
    // cancelSelection();
    // return;
    // }
    // }
    // super.approveSelection();
    // }
    // };
    // fileChooser.setDialogTitle("Select output filename");
    // int result = fileChooser.showSaveDialog(null);
    // if (result == JFileChooser.APPROVE_OPTION)
    // {
    // return fileChooser.getSelectedFile()
    // .getAbsolutePath();
    // }
    //
    // return null;
    // }

    // private static void printRowCountResult(QueryFileModel file, int rowCount)
    // {
    // file.getQuerySession()
    // .printLine(String.valueOf(rowCount) + " row(s) selected" + System.lineSeparator());
    // }
    //
    // private static void printBytesWritten(QueryFileModel file, long bytes, String filename)
    // {
    // file.getQuerySession()
    // .printLine(FileUtils.byteCountToDisplaySize(bytes) + " written to " + filename + System.lineSeparator());
    // }

    /** Get named parameters from query. */
    static Set<String> getVariables(String query)
    {
        QueryStatement parsedQuery;
        try
        {
            parsedQuery = PARSER.parseQuery(query);
        }
        catch (Exception e)
        {
            // TODO: notify error parsing
            return emptySet();
        }
        Set<String> parameters = new HashSet<>();
        parsedQuery.getStatements()
                .forEach(s -> s.accept(VARIABLES_VISITOR, parameters));
        return parameters;
    }

    /** Variable visitor. */
    private static class VariableVisitor extends AStatementVisitor<Void, Set<String>>
    {
        private static final ExpressionVisitor EXPRESSION_VISITOR = new ExpressionVisitor();

        @Override
        protected void visitExpression(Set<String> context, Expression expression)
        {
            expression.accept(EXPRESSION_VISITOR, context);
        }

        /** Expression visitor. */
        private static class ExpressionVisitor extends AExpressionVisitor<Void, Set<String>>
        {
            @Override
            public Void visit(VariableExpression expression, Set<String> context)
            {
                context.add(expression.getName());
                return null;
            }
        }
    }

    // /** Output writer used in NONE output mode. */
    // private static class NoneOutputWriter implements OutputWriter
    // {
    // private final QueryFileModel file;
    // private int rowCount;
    //
    // NoneOutputWriter(QueryFileModel file)
    // {
    // this.file = file;
    // }
    //
    // @Override
    // public void endRow()
    // {
    // file.incrementTotalRowCount();
    // rowCount++;
    // }
    //
    // @Override
    // public void writeFieldName(String name)
    // {
    // }
    //
    // @Override
    // public void writeValue(Object value)
    // {
    // }
    //
    // @Override
    // public void startObject()
    // {
    // }
    //
    // @Override
    // public void endObject()
    // {
    // }
    //
    // @Override
    // public void startArray()
    // {
    // }
    //
    // @Override
    // public void endArray()
    // {
    // }
    // }
    //
    // /** Writer that writes object structure from a projection. */
    // static class TableOutputWriter implements OutputWriter
    // {
    // private ResultModel resultModel;
    //
    // private final Stack<Object> parent = new Stack<>();
    // private final Stack<String> currentField = new Stack<>();
    //
    // void setResultModel(ResultModel resultModel)
    // {
    // this.resultModel = resultModel;
    // }
    //
    // /** Returns written value and clears state. */
    // private PairList getValue(int rowNumber)
    // {
    // currentField.clear();
    // Object v = parent.pop();
    // if (!(v instanceof PairList))
    // {
    // throw new RuntimeException("Expected a list of string/value pairs but got " + v);
    // }
    //
    // PairList result = (PairList) v;
    // result.add(0, Pair.of("", rowNumber));
    // return (PairList) v;
    // }
    //
    // @Override
    // public void initResult(String[] columns)
    // {
    // resultModel.setColumns(columns);
    // }
    //
    // @Override
    // public void endRow()
    // {
    // if (parent.isEmpty())
    // {
    // return;
    // }
    //
    // // Adjust columns
    // PairList pairList = getValue(resultModel.getRowCount() + 1);
    // // Adjust columns in model
    // if (!pairList.matchesModelColumns)
    // {
    // int count = resultModel.getColumnCount();
    // int listCount = pairList.size();
    // int newColumnsAdjust = 0;
    // boolean changeModelColumns = count != listCount;
    //
    // // Don't need to check row_id (first) columns
    // for (int i = 1; i < count; i++)
    // {
    // String modelColumn = resultModel.getColumns()[i];
    // String listColumn = (i + newColumnsAdjust) < listCount ? pairList.get(i + newColumnsAdjust)
    // .getKey()
    // : null;
    //
    // // New column that is about to be added last, mark
    // // model to be changed and move on
    // if (listColumn == null)
    // {
    // changeModelColumns = true;
    // continue;
    // }
    //
    // // Find out if we should pad previous values or new rows values
    // if (!modelColumn.equalsIgnoreCase(listColumn))
    // {
    // // Step forward in pairList until we find the current column
    // int c = findListColumn(pairList, modelColumn, i + newColumnsAdjust);
    // // CSOFF
    // if (c == -1)
    // // CSON
    // {
    // // Pad current row with null
    // pairList.add(i + newColumnsAdjust, Pair.of(modelColumn, null));
    // }
    // else
    // {
    // changeModelColumns = true;
    // newColumnsAdjust += c;
    // resultModel.moveValues(i, c);
    // }
    // }
    // }
    //
    // if (changeModelColumns)
    // {
    // resultModel.setColumns(pairList.columns.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    // }
    // }
    //
    // resultModel.addRow(pairList);
    // }
    //
    // /**
    // * Tries to find provided column in povided list starting at start index.
    // *
    // * @return Returns number of steps away or -1 if not found
    // */
    // private int findListColumn(PairList list, String column, int startIndex)
    // {
    // int size = list.size();
    // int steps = 0;
    // for (int i = startIndex; i < size; i++)
    // {
    // if (column.equalsIgnoreCase(list.get(i)
    // .getKey()))
    // {
    // return steps;
    // }
    // steps++;
    // }
    // return -1;
    // }
    //
    // @Override
    // public void writeFieldName(String name)
    // {
    // currentField.push(name);
    // }
    //
    // @Override
    // public void writeValue(Object input)
    // {
    // Object value = input;
    // if (value instanceof Iterator)
    // {
    // @SuppressWarnings("unchecked")
    // Iterator<Object> it = (Iterator<Object>) value;
    // startArray();
    // while (it.hasNext())
    // {
    // writeValue(it.next());
    // }
    // endArray();
    // return;
    // }
    // else if (value instanceof Reader)
    // {
    // try (Reader reader = (Reader) value)
    // {
    // value = IOUtils.toString(reader);
    // }
    // catch (IOException e)
    // {
    // throw new RuntimeException("Error reading reader to string", e);
    // }
    // }
    //
    // putValue(value);
    // }
    //
    // @Override
    // public void startObject()
    // {
    // // Root object should not be a map
    // // since we might have duplicate column names
    // if (parent.size() == 0)
    // {
    // // CSOFF
    // parent.push(new PairList(10));
    // // CSON
    // }
    // else
    // {
    // parent.push(new LinkedHashMap<>());
    // }
    // }
    //
    // @Override
    // public void endObject()
    // {
    // putValue(parent.pop());
    // }
    //
    // @Override
    // public void startArray()
    // {
    // parent.push(new ArrayList<>());
    // }
    //
    // @Override
    // public void endArray()
    // {
    // putValue(parent.pop());
    // }
    //
    // @SuppressWarnings("unchecked")
    // private void putValue(Object value)
    // {
    // // Top of stack put value back
    // if (parent.isEmpty())
    // {
    // parent.push(value);
    // return;
    // }
    //
    // Object p = parent.peek();
    //
    // if (p instanceof PairList)
    // {
    // // Find out where to put this entry
    // PairList list = (PairList) p;
    // String column = currentField.pop();
    // // Adjust for row_id column
    // int columnIndex = list.size() + 1;
    //
    // // Flag that the list is not matching model columns
    // // model needs to adjust later on
    // if (list.matchesModelColumns
    // && (columnIndex >= resultModel.getColumnCount()
    // || !column.equalsIgnoreCase(resultModel.getColumns()[columnIndex])))
    // {
    // list.matchesModelColumns = false;
    // }
    //
    // list.add(Pair.of(column, value));
    // }
    // else if (p instanceof Map)
    // {
    // ((Map<String, Object>) p).put(currentField.pop(), value);
    // }
    // else if (p instanceof List)
    // {
    // ((List<Object>) p).add(value);
    // }
    // }
    //
    // /** Pair list. */
    // static class PairList extends ArrayList<Pair<String, Object>>
    // {
    // static final PairList EMPTY = new PairList(0);
    // private final List<String> columns;
    // private boolean matchesModelColumns = true;
    //
    // private PairList(int capacity)
    // {
    // super(capacity);
    // columns = new ArrayList<>(capacity);
    // }
    //
    // List<String> getColumns()
    // {
    // return columns;
    // }
    //
    // @Override
    // public void add(int index, Pair<String, Object> pair)
    // {
    // columns.add(index, pair.getKey());
    // super.add(index, pair);
    // }
    //
    // @Override
    // public boolean add(Pair<String, Object> pair)
    // {
    // columns.add(pair.getKey());
    // return super.add(pair);
    // }
    // }
    // }
}
