package se.kuseman.payloadbuilder.catalog;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import se.kuseman.payloadbuilder.catalog.jdbc.model.Column;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Constraint;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKey;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ForeignKeyColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.Index;
import se.kuseman.payloadbuilder.catalog.jdbc.model.IndexColumn;
import se.kuseman.payloadbuilder.catalog.jdbc.model.ObjectName;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource;
import se.kuseman.payloadbuilder.catalog.jdbc.model.TableSource.Type;

//CSOFF
public class TemplateUtilsTest extends Assert
{
    @SuppressWarnings("deprecation")
    @Test
    public void test() throws IOException
    {
        String template = Common.readResource("/se/kuseman/payloadbuilder/catalog/jdbc/templates/Table.html");

        //@formatter:off
        Map<String, Object> model = Map.of(
                "table", new TableSource("cat", "schem", "tab", Type.TABLE, asList(new Column("column", "nvarchar", 100, 0, 0, false, "pk_tab"))),
                "foreignKeys", asList(new ForeignKey(new ObjectName("cat", "schem", "fk1"), asList(new ForeignKeyColumn(new ObjectName("conCat", "conSchem", "conName"), "conCol", new ObjectName("refCat", "refSchem", "refName"), "refCol")))),
                "indices", asList(new Index(new ObjectName("cat", "schem", "tab"), "ix_1", true, asList(new IndexColumn("ix_col", true)))),
                "constraints", asList(new Constraint(new ObjectName("cat", "schem", "tab"), "ck_con", Constraint.Type.CHECK, "col1", "col > 10"),  new Constraint(new ObjectName("cat", "schem", "tab"), "df_con", Constraint.Type.DEFAULT, "modificationDate", "getdate()"))
                );
        //@formatter:on

        FileUtils.write(new File("e:/Temp/table.html"), TemplateUtils.process("test", template, model));
    }

}
// CSON
