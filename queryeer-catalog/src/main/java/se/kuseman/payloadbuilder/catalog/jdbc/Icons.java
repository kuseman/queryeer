package se.kuseman.payloadbuilder.catalog.jdbc;

import static java.util.Objects.requireNonNull;

import javax.swing.Icon;

import com.queryeer.api.extensions.Inject;
import com.queryeer.api.service.IIconFactory;
import com.queryeer.api.service.IIconFactory.Provider;

/** Icons constant used in JDBC module */
@Inject
public class Icons
{
    private IIconFactory iconFactory;
    // CSOFF
    public final Icon database;
    public final Icon lock;
    public final Icon unlock;
    public final Icon server;
    public final Icon folder_o;
    public final Icon columns;
    public final Icon table;
    public final Icon plug;
    public final Icon tableIcon;
    public final Icon viewIcon;
    public final Icon fileTextIcon;
    public final Icon wpformsIcon;
    public final Icon keyIcon;
    public final Icon columnsIcon;
    public final Icon reorderIcon;
    public final Icon atIcon;
    public final Icon boltIcon;

    // CSON

    private Icons(IIconFactory iconFactory)
    {
        this.iconFactory = requireNonNull(iconFactory, "iconFactory");
        database = iconFactory.getIcon(Provider.FONTAWESOME, "DATABASE");
        lock = iconFactory.getIcon(Provider.FONTAWESOME, "LOCK");
        unlock = iconFactory.getIcon(Provider.FONTAWESOME, "UNLOCK");
        server = iconFactory.getIcon(Provider.FONTAWESOME, "SERVER");
        folder_o = iconFactory.getIcon(Provider.FONTAWESOME, "FOLDER_O");
        columns = iconFactory.getIcon(Provider.FONTAWESOME, "COLUMNS");
        table = iconFactory.getIcon(Provider.FONTAWESOME, "TABLE");
        wpformsIcon = iconFactory.getIcon(Provider.FONTAWESOME, "WPFORMS");
        plug = iconFactory.getIcon(Provider.FONTAWESOME, "PLUG");
        tableIcon = iconFactory.getIcon(Provider.FONTAWESOME, "TABLE");
        viewIcon = iconFactory.getIcon(Provider.FONTAWESOME, "COPY");
        fileTextIcon = iconFactory.getIcon(Provider.FONTAWESOME, "FILE_TEXT_O");
        keyIcon = iconFactory.getIcon(Provider.FONTAWESOME, "KEY");
        columnsIcon = iconFactory.getIcon(Provider.FONTAWESOME, "COLUMNS");
        reorderIcon = iconFactory.getIcon(Provider.FONTAWESOME, "REORDER");
        atIcon = iconFactory.getIcon(Provider.FONTAWESOME, "AT");
        boltIcon = iconFactory.getIcon(Provider.FONTAWESOME, "BOLT");
    }

    public IIconFactory getIconFactory()
    {
        return iconFactory;
    }
}
