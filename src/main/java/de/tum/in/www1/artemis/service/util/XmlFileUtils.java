package de.tum.in.www1.artemis.service.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlFileUtils {

    private static final Logger log = LoggerFactory.getLogger(XmlFileUtils.class);

    public static Document readFromString(String xmlString) {
        return parseDocument(xmlString);
    }

    /**
     * Reads an XMl file from the resources for a given relative path
     *
     * @param path The path in the resources under which the XML file is stored
     * @return The parsed XML file
     */
    public static Document readXmlFile(Path path) {
        return readXmlFile(path, null);
    }

    /**
     * Reads an XMl file from the resources for a given relative path. Also replaces all Strings in the given file
     * based on the map parameter. Meaning key in the map -> replaced by mapped value
     *
     * @param path The path in the resources under which the XML file is stored
     * @param variablesToReplace A map containing key, that should get replaced by their mapped values
     * @return The parsed XML file with the replaced values
     */
    public static Document readXmlFile(Path path, @Nullable Map<String, String> variablesToReplace) {
        final var configXmlFile = new File(XmlFileUtils.class.getClassLoader().getResource(path.toString()).getFile());
        try {
            var configXmlText = FileUtils.readFileToString(configXmlFile, CharEncoding.UTF_8);
            if (variablesToReplace != null) {
                for (final var replacement : variablesToReplace.entrySet()) {
                    configXmlText = configXmlText.replace(replacement.getKey(), replacement.getValue());
                }
            }

            return parseDocument(configXmlText);
        }
        catch (IOException e) {
            final var errorMessage = "Error loading template Jenins build XML: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private static Document parseDocument(String configXmlText) {
        try {
            final var domFactory = DocumentBuilderFactory.newInstance();
            final var builder = domFactory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(configXmlText)));
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            final var errorMessage = "Error loading template Jenins build XML: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }
}
