package de.tum.cit.aet.artemis.service.connectors.localci.scaparser.strategy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import de.tum.cit.aet.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
record BugInstance(@JacksonXmlProperty(isAttribute = true, localName = "type") String type,

        @JacksonXmlProperty(isAttribute = true, localName = "category") String category,

        @JacksonXmlProperty(isAttribute = true, localName = "priority") String priority,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "SourceLine") List<SourceLine> sourceLines,

        @JacksonXmlProperty(localName = "LongMessage") String longMessage) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record SourceLine(@JacksonXmlProperty(isAttribute = true, localName = "sourcepath") String sourcePath,

        @JacksonXmlProperty(isAttribute = true, localName = "start") int start,

        @JacksonXmlProperty(isAttribute = true, localName = "end") int end) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Project(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "SrcDir") List<String> srcDirs) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record BugCollection(@JacksonXmlProperty(localName = "Project") Project project,

        @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "BugInstance") List<BugInstance> bugInstances) {
}

class SpotbugsParser implements ParserStrategy {

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public StaticCodeAnalysisReportDTO parse(String xmlContent) {
        try {
            BugCollection bugCollection = xmlMapper.readValue(xmlContent, BugCollection.class);
            return createReportFromBugCollection(bugCollection);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromBugCollection(BugCollection bugCollection) {
        List<StaticCodeAnalysisIssue> issues = new ArrayList<>();

        if (bugCollection.bugInstances() == null) {
            return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.SPOTBUGS, issues);
        }

        String sourceDirectory = bugCollection.project().srcDirs().isEmpty() ? "" : bugCollection.project().srcDirs().getFirst();
        if (!sourceDirectory.endsWith(File.separator)) {
            sourceDirectory += File.separator;
        }

        for (BugInstance bugInstance : bugCollection.bugInstances()) {
            for (SourceLine sourceLine : bugInstance.sourceLines()) {
                String unixPath = ParserStrategy.transformToUnixPath(sourceDirectory + sourceLine.sourcePath());
                StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(unixPath, sourceLine.start(), sourceLine.end(), null, null, bugInstance.type(), bugInstance.category(),
                        bugInstance.longMessage(), bugInstance.priority(), null); // The penalty is decided by the course instructor, there is no penalty information in the xml
                issues.add(issue);
            }
        }
        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.SPOTBUGS, issues);
    }

}
