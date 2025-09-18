package com.queryeer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter.Indenter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Variuos utils used by Queryeer */
final class Utils
{
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.)?");
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
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        WRITER = mapper.writer(printer);
        READER = mapper.readerFor(Object.class);
    }

    /** Compare 2 semver strings */
    static int compareVersions(String nameA, String nameB)
    {
        int[] componentsA = getVersionComponents(nameA);
        int[] componentsB = getVersionComponents(nameB);
        for (int i = 0; i < 3; i++)
        {
            int c = Integer.compare(componentsA[i], componentsB[i]);
            if (c != 0)
            {
                return -1 * c;
            }
        }

        return 0;
    }

    /**
     * Tries to find 3 semver components from provided string value. Returns a 3 item int array with found components
     */
    private static int[] getVersionComponents(String value)
    {
        int[] components = new int[] { -1, -1, -1 };
        Matcher matcher = VERSION_PATTERN.matcher(value);
        int index = 0;
        while (matcher.find())
        {
            components[index++] = Integer.parseInt(matcher.group(1));
            if (index == 3)
            {
                break;
            }
        }
        return components;
    }

    /** Fetches latest version (tag) from github */
    static String getLatestTag()
    {
        HttpURLConnection c = null;
        try
        {
            URL u = new URL("https://api.github.com/repos/kuseman/queryeer/releases");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(1500);
            c.setReadTimeout(5000);
            c.connect();
            int status = c.getResponseCode();

            if (status != 200)
            {
                return null;
            }

            try (InputStream is = c.getInputStream())
            {
                return QueryeerController.MAPPER.readValue(is, new TypeReference<List<Map<String, Object>>>()
                {
                })
                        .stream()
                        .map(m -> (String) m.get("tag_name"))
                        .sorted(Utils::compareVersions)
                        .findAny()
                        .orElse(null);
            }
        }
        catch (Exception e)
        {
            System.err.println("Error fetching latest tags: " + e.getMessage());
        }
        finally
        {
            if (c != null)
            {
                try
                {
                    c.disconnect();
                }
                catch (Exception ex)
                {
                    System.err.println("Error fetching latest tags");
                }
            }
        }
        return null;
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
