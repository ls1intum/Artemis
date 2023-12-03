package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils.XmlUtils;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

public class ParserContext {

    /**
     * Builds the document using the provided file and parses it to a Report object.
     *
     * @param file File referencing the static code analysis report
     * @return Report containing the static code analysis issues
     * @throws UnsupportedToolException     if the static code analysis tool which created the report is not supported
     * @throws IOException                  if the file could not be read
     * @throws ParserConfigurationException if no parser could be created
     * @throws SAXException                 if a parsing error occurs
     */
    public StaticCodeAnalysisReportDTO getReport(File file) throws IOException, ParserConfigurationException, SAXException {
        final DocumentBuilder builder = XmlUtils.createDocumentBuilder();
        final Document document = builder.parse(file);
        return parseDocument(document);
    }

    private StaticCodeAnalysisReportDTO parseDocument(Document doc) {
        ParserPolicy parserPolicy = new ParserPolicy();
        ParserStrategy parserStrategy = parserPolicy.configure(doc);
        return parserStrategy.parse(doc);
    }
}
