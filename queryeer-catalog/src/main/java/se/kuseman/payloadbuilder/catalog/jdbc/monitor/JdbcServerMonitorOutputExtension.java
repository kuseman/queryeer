package se.kuseman.payloadbuilder.catalog.jdbc.monitor;

import static java.util.Objects.requireNonNull;

import com.queryeer.api.IQueryFile;
import com.queryeer.api.component.IDialogFactory;
import com.queryeer.api.extensions.output.IOutputComponent;
import com.queryeer.api.extensions.output.IOutputExtension;
import com.queryeer.api.service.IIconFactory;

import se.kuseman.payloadbuilder.api.OutputWriter;
import se.kuseman.payloadbuilder.catalog.jdbc.IConnectionState;

/**
 * Output extension that adds a "Server Monitor" tab to all JDBC query files. The tab continuously monitors the connected SQL Server (or other supported databases) showing active sessions, wait
 * statistics, locks, and blocking chains.
 */
public class JdbcServerMonitorOutputExtension implements IOutputExtension
{
    private final IIconFactory iconFactory;
    private final IDialogFactory dialogFactory;

    public JdbcServerMonitorOutputExtension(IIconFactory iconFactory, IDialogFactory dialogFactory)
    {
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
        this.dialogFactory = requireNonNull(dialogFactory, "dialogFactory");
    }

    @Override
    public String getTitle()
    {
        return "Server Monitor";
    }

    @Override
    public boolean isAutoAdded()
    {
        return true;
    }

    @Override
    public boolean isAutoPopulated()
    {
        return false;
    }

    @Override
    public int order()
    {
        return 100;
    }

    @Override
    public IOutputComponent createResultComponent(IQueryFile file)
    {
        // Only attach to JDBC query files
        if (!(file.getEngineState() instanceof IConnectionState connectionState))
        {
            return null;
        }
        return new ServerMonitorOutputComponent(connectionState, this, iconFactory, dialogFactory);
    }

    @Override
    public OutputWriter createOutputWriter(IQueryFile file)
    {
        // This extension does not consume query output
        return null;
    }
}
