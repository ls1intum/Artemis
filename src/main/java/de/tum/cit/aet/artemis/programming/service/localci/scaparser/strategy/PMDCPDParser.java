package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
record PmdCpc(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "duplication") List<Duplication> duplications) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Duplication(@JacksonXmlProperty(isAttribute = true, localName = "lines") int lines,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "file") List<DuplicationFile> files) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
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
            PmdCpc duplication = xmlMapper.readValue(xmlContent, PmdCpc.class);
            return createReportFromDuplication(duplication);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromDuplication(PmdCpc report) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();
        if (report.duplications() == null) {
            return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.PMD_CPD, issues);
        }
        for (var duplication : report.duplications()) {
            if (duplication.files() == null) {
                continue;
            }

            StringBuilder messageBuilder = new StringBuilder("Code duplication of ").append(duplication.lines()).append(" lines in the following files:");
            // Iterate through the files to create on commonly used error message refering to all instances of duplication.
            for (DuplicationFile file : duplication.files()) {
                String unixPath = ParserStrategy.transformToUnixPath(file.path());
                String filename = new File(unixPath).getName();
                messageBuilder.append("\n - ").append(filename).append(": Lines ").append(file.startLine()).append(" to ").append(file.endLine());
            }

            String message = messageBuilder.toString();
            // We create a new issue for every instance of duplicated code blocks.
            for (DuplicationFile file : duplication.files()) {
                String unixPath = ParserStrategy.transformToUnixPath(file.path());
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(unixPath, file.startLine(), file.endLine(), file.startColumn(), file.endColumn(), CPD_CATEGORY,
                        CPD_CATEGORY, message, null,  // Priority might not be applicable
                        null   // Penalty not applicable
                );
                issues.add(issue);
            }
        }

        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.PMD_CPD, issues);
    }
}
