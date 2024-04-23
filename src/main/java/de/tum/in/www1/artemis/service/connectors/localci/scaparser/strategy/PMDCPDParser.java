package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisIssue;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

record Duplication(@JacksonXmlProperty(isAttribute = true, localName = "lines") int lines,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "file") List<DuplicationFile> files) {
}

record DuplicationFile(@JacksonXmlProperty(isAttribute = true, localName = "path") String path,

        @JacksonXmlProperty(isAttribute = true, localName = "line") int startLine,

        @JacksonXmlProperty(isAttribute = true, localName = "endline") int endLine,

        @JacksonXmlProperty(isAttribute = true, localName = "column") int startColumn,

        @JacksonXmlProperty(isAttribute = true, localName = "endcolumn") int endColumn) {
}

class PMDCPDParser implements ParserStrategy {

    private static final String CPD_CATEGORY = "Copy/Paste Detection";

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public StaticCodeAnalysisReportDTO parse(String xmlContent) {
        try {
            Duplication duplication = xmlMapper.readValue(xmlContent, Duplication.class);
            return createReportFromDuplication(duplication);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromDuplication(Duplication duplication) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        String message = "Code duplication of " + duplication.lines() + " lines in the following files:";

        for (DuplicationFile file : duplication.files()) {
            String unixPath = ParserStrategy.transformToUnixPath(file.path());
            String filename = new File(unixPath).getName();
            message += "\n - " + filename + ": Lines " + file.startLine() + " to " + file.endLine();

            StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(unixPath, file.startLine(), file.endLine(), file.startColumn(), file.endColumn(), CPD_CATEGORY,
                    CPD_CATEGORY, message, null,  // Priority might not be applicable
                    null   // Penalty not applicable
            );
            issues.add(issue);
        }

        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.PMD_CPD, issues);
    }
}
