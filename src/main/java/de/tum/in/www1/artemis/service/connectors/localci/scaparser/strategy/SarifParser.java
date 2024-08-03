package de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisIssue;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
record SarifLog(List<Run> runs) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Run(List<Result> results, Tool tool) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Tool(ToolComponent driver) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record ToolComponent(String name) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Result(String ruleId, List<Location> locations, Message message, String level) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Message(String text) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Location(PhysicalLocation physicalLocation) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record PhysicalLocation(ArtifactLocation artifactLocation, Region region) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record ArtifactLocation(String uri) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record Region(int startLine, int startColumn, int endLine, int endColumn) {
}

public class SarifParser implements ParserStrategy {

    private final ObjectReader objectReader = new ObjectMapper().readerFor(SarifLog.class);

    @Override
    public StaticCodeAnalysisReportDTO parse(String reportContent) {
        try {
            final SarifLog sarifLog = objectReader.readValue(reportContent);
            return createReportFromSarifLog(sarifLog);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse SARIF", e);
        }
    }

    private StaticCodeAnalysisReportDTO createReportFromSarifLog(SarifLog sarifLog) {
        if (sarifLog.runs().size() != 1) {
            throw new RuntimeException("Sarif report has to contain exactly one run");
        }

        final var run = sarifLog.runs().getFirst();

        String toolName = run.tool().driver().name();
        final var tool = switch (toolName) {
            case "clippy" -> StaticCodeAnalysisTool.CLIPPY;
            default -> throw new IllegalStateException("Report was produced by an unknown tool: " + toolName);
        };

        final var issues = run.results().stream().map(result -> {
            if (result.locations().size() != 1) {
                throw new RuntimeException("Expected exactly one location");
            }
            final var location = result.locations().getFirst().physicalLocation();
            final var filePath = location.artifactLocation().uri();
            final var region = location.region();

            return new StaticCodeAnalysisIssue(filePath, region.startLine(), region.endLine(), region.startColumn(), region.endColumn(), result.ruleId(), "default",  // TODO: map
                                                                                                                                                                      // ruleId to
                                                                                                                                                                      // category,
                                                                                                                                                                      // see
                                                                                                                                                                      // https://rust-lang.github.io/rust-clippy/master/index.html
                    result.message().text(), result.level(), null);
        }).toList();

        return new StaticCodeAnalysisReportDTO(tool, issues);
    }
}
