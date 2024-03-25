package com.queryeer.dialog;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.Indenter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

/** Dialog utils */
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

    /** Return pretty json for provided value */
    static String formatJson(Object value)
    {
        try
        {
            // See if the input value is a JSON string already
            if (value instanceof String)
            {
                try
                {
                    Object v = READER.readValue((String) value);
                    value = v;
                }
                catch (IOException e)
                {
                }
            }

            return Utils.WRITER.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            return StringUtils.EMPTY;
        }
    }

    /** Format provided xml */
    static String formatXML(String xml)
    {
        try
        {
            final InputSource src = new InputSource(new StringReader(xml));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            final Node document = factory.newDocumentBuilder()
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
