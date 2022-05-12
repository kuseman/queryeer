package com.queryeer.output.table;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Node;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.Indenter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

/** Table output utils */
class Utils
{
    static final ObjectWriter WRITER;
    static final ObjectReader READER;
    private static final DOMImplementationLS DOM;

    static
    {
        try
        {
            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOM = (DOMImplementationLS) registry.getDOMImplementation("LS");
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error creating XML factory");
        }

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentArraysWith(new Indenter()
        {
            @Override
            public void writeIndentation(JsonGenerator g, int level) throws IOException
            {
                DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.writeIndentation(g, level);
            }

            @Override
            public boolean isInline()
            {
                return false;
            }
        });

        ObjectMapper mapper = new ObjectMapper();
        WRITER = mapper.writer(printer);
        READER = mapper.readerFor(Object.class);
    }

    /** Format provided xml */
    static String formatXML(String xml)
    {
        try
        {
            final InputSource src = new InputSource(new StringReader(xml));
            final Node document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(src)
                    .getDocumentElement();
            final Boolean keepDeclaration = Boolean.valueOf(xml.startsWith("<?xml"));

            final LSSerializer writer = DOM.createLSSerializer();

            writer.getDomConfig()
                    .setParameter("format-pretty-print", Boolean.TRUE);
            writer.getDomConfig()
                    .setParameter("xml-declaration", keepDeclaration);

            return writer.writeToString(document);
        }
        catch (Exception e)
        {
            // Return original upon error
            return xml;
        }
    }
}
