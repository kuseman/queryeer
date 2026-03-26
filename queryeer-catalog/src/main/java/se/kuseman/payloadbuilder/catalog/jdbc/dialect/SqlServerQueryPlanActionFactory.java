package se.kuseman.payloadbuilder.catalog.jdbc.dialect;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.Strings.CI;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputComponent;
import com.queryeer.api.extensions.output.queryplan.IQueryPlanOutputExtension;
import com.queryeer.api.extensions.output.queryplan.Node;
import com.queryeer.api.extensions.output.table.ITableContextMenuAction;
import com.queryeer.api.extensions.output.table.ITableContextMenuActionFactory;
import com.queryeer.api.extensions.output.table.ITableOutputComponent;
import com.queryeer.api.service.IQueryFileProvider;

/** Tries to parse an XML cell in table to a query plan */
class SqlServerQueryPlanActionFactory implements ITableContextMenuActionFactory
{
    private final IQueryFileProvider provider;
    private final IQueryPlanOutputExtension queryPlanOutputExtension;

    SqlServerQueryPlanActionFactory(IQueryFileProvider provider, IQueryPlanOutputExtension queryPlanOutputExtension)
    {
        this.provider = requireNonNull(provider);
        this.queryPlanOutputExtension = requireNonNull(queryPlanOutputExtension);
    }

    @Override
    public List<ITableContextMenuAction> create(final ITableOutputComponent outputcomponent)
    {
        return List.of(new ITableContextMenuAction()
        {
            @Override
            public int order()
            {
                return 100;
            }

            @Override
            public Action getAction()
            {
                return new AbstractAction("Show SQL Server Query Plan")
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        List<ITableOutputComponent.SelectedCell> cells = outputcomponent.getSelectedCells();
                        if (cells.isEmpty())
                        {
                            return;
                        }
                        Object value = cells.get(0)
                                .getCellValue();
                        String xml = String.valueOf(value);
                        if (isSqlServerQueryPlan(xml))
                        {
                            IQueryFile queryFile = provider.getCurrentFile();
                            if (queryFile != null)
                            {
                                parseAndShowQueryPlan(queryFile, queryPlanOutputExtension, xml, true);
                            }
                        }
                    }
                };
            }

            @Override
            public boolean showContextMenu(ITableOutputComponent.SelectedCell selectedCell)
            {
                return isSqlServerQueryPlan(String.valueOf(selectedCell.getCellValue()));
            }
        });
    }

    static boolean isSqlServerQueryPlan(String value)
    {
        return CI.startsWith(value, "<ShowPlanXML");
    }

    static void parseAndShowQueryPlan(IQueryFile queryFile, IQueryPlanOutputExtension queryPlanOutputExtension, String xml, boolean switchToQueryPlanOutput)
    {
        List<Node> nodes = SqlServerQueryPlanParser.parseXml(xml);
        if (nodes != null)
        {
            SwingUtilities.invokeLater(() ->
            {
                IQueryPlanOutputComponent outputComponent = queryFile.getOutputComponent(IQueryPlanOutputComponent.class);
                if (outputComponent == null)
                {
                    outputComponent = (IQueryPlanOutputComponent) queryPlanOutputExtension.createResultComponent(queryFile);
                    queryFile.addOutputComponent(outputComponent);
                }

                if (switchToQueryPlanOutput)
                {
                    outputComponent.clearState();
                    queryFile.selectOutputComponent(IQueryPlanOutputComponent.class);
                }

                for (Node node : nodes)
                {
                    outputComponent.addQueryPlan(node);
                }

            });
        }
    }
}
