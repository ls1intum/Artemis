package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.ParserStrategy;

public class SarifParser implements ParserStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StaticCodeAnalysisTool tool;

    private final RuleCategorizer ruleCategorizer;

    public SarifParser(StaticCodeAnalysisTool tool, RuleCategorizer ruleCategorizer) {
        this.tool = tool;
        this.ruleCategorizer = ruleCategorizer;
    }

    @Override
    public StaticCodeAnalysisReportDTO parse(String xmlContent) {
        SarifLog sarifLog;
        try {
            sarifLog = objectMapper.readValue(xmlContent, SarifLog.class);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Run run = sarifLog.getRuns().getFirst();
        Map<String, ReportingDescriptor> ruleMap = run.getTool().getDriver().getRules().stream().collect(Collectors.toMap(ReportingDescriptor::getId, rule -> rule));

        List<StaticCodeAnalysisIssue> issues = run.getResults().stream().map(result -> {
            PhysicalLocation location = result.getLocations().getFirst().getPhysicalLocation();
            Region region = location.getRegion();

            ReportingDescriptor rule = ruleMap.get(result.getRuleId());
            String category = ruleCategorizer.categorizeRule(rule);

            URI uri = URI.create(location.getArtifactLocation().getUri());
            String path = uri.getPath();

            return new StaticCodeAnalysisIssue(path, region.getStartLine(), region.getEndLine(), region.getStartColumn(), region.getEndColumn(), result.getRuleId(), category,
                    result.getMessage().getText(), result.getLevel().toString(), null);
        }).toList();

        return new StaticCodeAnalysisReportDTO(tool, issues);
    }
}
