package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.ParserStrategy;

/**
 * Implements parts of the SARIF OASIS standard version 2.1.0.
 *
 * @see <a href="https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/sarif-v2.1.0-errata01-os-complete.html">SARIF specification</a>
 */
public class SarifParser implements ParserStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StaticCodeAnalysisTool tool;

    private final RuleCategorizer ruleCategorizer;

    public SarifParser(StaticCodeAnalysisTool tool, RuleCategorizer ruleCategorizer) {
        this.tool = tool;
        this.ruleCategorizer = ruleCategorizer;
    }

    @Override
    public StaticCodeAnalysisReportDTO parse(String reportContent) {
        SarifLog sarifLog;
        try {
            sarifLog = objectMapper.readValue(reportContent, SarifLog.class);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Run run = sarifLog.getRuns().getFirst();
        ToolComponent driver = run.getTool().getDriver();

        List<ReportingDescriptor> rules = driver.getRules().orElse(List.of());

        // Rule ids are not guaranteed to be unique. Use the first occurring for rule lookup.
        Map<String, ReportingDescriptor> ruleOfId = rules.stream().collect(Collectors.toMap(ReportingDescriptor::getId, Function.identity(), (first, next) -> first));

        List<Result> results = run.getResults().orElse(List.of());
        List<StaticCodeAnalysisIssue> issues = results.stream().map(result -> processResult(result, driver, ruleOfId)).toList();

        return new StaticCodeAnalysisReportDTO(tool, issues);
    }

    private StaticCodeAnalysisIssue processResult(Result result, ToolComponent driver, Map<String, ReportingDescriptor> ruleOfId) {
        PhysicalLocation location = result.getLocations().flatMap(locations -> locations.stream().findFirst()).flatMap(Location::getPhysicalLocation)
                .orElseThrow(() -> new RuntimeException("Location needed"));

        URI uri = URI.create(location.getArtifactLocation().flatMap(ArtifactLocation::getUri).orElseThrow(() -> new RuntimeException("File path needed")));
        String path = uri.getPath();

        Region region = location.getRegion().orElseThrow(() -> new RuntimeException("Region must be present"));
        int startLine = region.getStartLine().orElseThrow(() -> new RuntimeException("Text region needed"));
        int startColumn = region.getStartColumn().orElse(1);
        int endLine = region.getEndLine().orElse(startLine);
        int endColumn = region.getEndColumn().orElse(startColumn + 1);

        String ruleId = result.getRuleId()
                .orElseGet(() -> result.getRule().flatMap(ReportingDescriptorReference::getId).orElseThrow(() -> new RuntimeException("Either ruleId or rule.id must be present")));

        // ruleIndex can use -1 to indicate a missing value
        Optional<Integer> ruleIndexOrMinusOne = result.getRuleIndex().or(() -> result.getRule().flatMap(ReportingDescriptorReference::getIndex));
        Optional<Integer> ruleIndex = ruleIndexOrMinusOne.flatMap(index -> index != -1 ? Optional.of(index) : Optional.empty());

        Optional<ReportingDescriptor> ruleByIndex = driver.getRules().flatMap(rules -> ruleIndex.map(rules::get));
        Optional<ReportingDescriptor> rule = ruleByIndex.or(() -> lookupRuleById(ruleId, ruleOfId));

        // Fallback to the rule identifier for the category
        String category = rule.map(ruleCategorizer::categorizeRule).orElse(ruleId);

        Result.Level level = result.getLevel().orElse(Result.Level.WARNING);

        String message = result.getMessage().getText().orElseGet(() -> {
            String messageId = result.getMessage().getId().orElseThrow(() -> new RuntimeException("Either text or id must be present"));

            var ruleMessageString = rule.flatMap(ReportingDescriptor::getMessageStrings).map(MessageStrings::getAdditionalProperties).map(strings -> strings.get(messageId));
            var globalMessageString = driver.getGlobalMessageStrings().map(GlobalMessageStrings::getAdditionalProperties).map(strings -> strings.get(messageId));

            var messageString = ruleMessageString.or(() -> globalMessageString).orElseThrow(() -> new RuntimeException("Message lookup failed"));
            return messageString.getText();
        });

        return new StaticCodeAnalysisIssue(path, startLine, endLine, startColumn, endColumn, ruleId, category, message, level.toString(), null);
    }

    private static Optional<ReportingDescriptor> lookupRuleById(String ruleId, Map<String, ReportingDescriptor> ruleOfId) {
        return Optional.ofNullable(ruleOfId.get(ruleId)).or(() -> getBaseRuleId(ruleId).map(ruleOfId::get));
    }

    private static Optional<String> getBaseRuleId(String ruleId) {
        int hierarchySeperatorIndex = ruleId.lastIndexOf('/');
        if (hierarchySeperatorIndex == -1) {
            return Optional.empty();
        }
        String baseRuleId = ruleId.substring(0, hierarchySeperatorIndex);
        return Optional.of(baseRuleId);
    }

}
