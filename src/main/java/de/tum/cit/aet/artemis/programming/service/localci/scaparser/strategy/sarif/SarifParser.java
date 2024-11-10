package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ArtifactLocation;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.GlobalMessageStrings;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Location;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.MessageStrings;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.PhysicalLocation;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Region;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptorReference;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Result;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Run;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.SarifLog;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ToolComponent;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.ParserStrategy;

/**
 * Implements parts of the SARIF OASIS standard version 2.1.0.
 *
 * @see <a href="https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/sarif-v2.1.0-errata01-os-complete.html">SARIF specification</a>
 */
public class SarifParser implements ParserStrategy {

    private static final Logger log = LoggerFactory.getLogger(SarifParser.class);

    private static class SarifFormatException extends RuntimeException {

        private SarifFormatException(String message) {
            super(message);
        }
    }

    private static class InformationMissingException extends RuntimeException {

        private InformationMissingException(String message) {
            super(message);
        }
    }

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
        List<StaticCodeAnalysisIssue> issues = results.stream().map(result -> tryProcessResult(result, driver, ruleOfId)).filter(Objects::nonNull).toList();

        return new StaticCodeAnalysisReportDTO(tool, issues);
    }

    private StaticCodeAnalysisIssue tryProcessResult(Result result, ToolComponent driver, Map<String, ReportingDescriptor> ruleOfId) {
        try {
            return processResult(result, driver, ruleOfId);
        }
        catch (SarifFormatException | NullPointerException e) {
            log.error("The result is malformed", e);
            return null;
        }
        catch (InformationMissingException e) {
            log.warn("The result does not contain required information", e);
            return null;
        }
    }

    private StaticCodeAnalysisIssue processResult(Result result, ToolComponent driver, Map<String, ReportingDescriptor> ruleOfId) throws SarifFormatException {
        PhysicalLocation location = result.getLocations().flatMap(locations -> locations.stream().findFirst()).flatMap(Location::getPhysicalLocation)
                .orElseThrow(() -> new InformationMissingException("Location needed"));

        URI uri = URI.create(location.getArtifactLocation().flatMap(ArtifactLocation::getUri).orElseThrow(() -> new InformationMissingException("File path needed")));
        String path = uri.getPath();

        Region region = location.getRegion().orElseThrow(() -> new SarifFormatException("Region must be present"));
        int startLine = region.getStartLine().orElseThrow(() -> new InformationMissingException("Text region needed"));
        int startColumn = region.getStartColumn().orElse(1);
        int endLine = region.getEndLine().orElse(startLine);
        int endColumn = region.getEndColumn().orElse(startColumn + 1);

        String ruleId = getRuleId(result);

        Optional<Integer> ruleIndex = getRuleIndex(result);

        Optional<ReportingDescriptor> ruleByIndex = driver.getRules().flatMap(rules -> ruleIndex.map(rules::get));
        Optional<ReportingDescriptor> rule = ruleByIndex.or(() -> lookupRuleById(ruleId, ruleOfId));

        // Fallback to the rule identifier for the category
        String category = rule.map(ruleCategorizer::categorizeRule).orElse(ruleId);

        Result.Level level = result.getLevel().orElse(Result.Level.WARNING);

        String message = findMessage(result, driver, rule);

        return new StaticCodeAnalysisIssue(path, startLine, endLine, startColumn, endColumn, ruleId, category, message, level.toString(), null);
    }

    private static String getRuleId(Result result) throws SarifFormatException {
        return result.getRuleId().orElseGet(
                () -> result.getRule().flatMap(ReportingDescriptorReference::getId).orElseThrow(() -> new SarifFormatException("Either ruleId or rule.id must be present")));
    }

    private static Optional<Integer> getRuleIndex(Result result) {
        // ruleIndex can use -1 to indicate a missing value
        Optional<Integer> ruleIndexOrMinusOne = result.getRuleIndex().or(() -> result.getRule().flatMap(ReportingDescriptorReference::getIndex));
        return ruleIndexOrMinusOne.flatMap(index -> index != -1 ? Optional.of(index) : Optional.empty());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String findMessage(Result result, ToolComponent driver, Optional<ReportingDescriptor> rule) throws SarifFormatException {
        return result.getMessage().getText().orElseGet(() -> lookupMessageById(result, driver, rule));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String lookupMessageById(Result result, ToolComponent driver, Optional<ReportingDescriptor> rule) throws SarifFormatException {
        String messageId = result.getMessage().getId().orElseThrow(() -> new SarifFormatException("Either text or id must be present"));

        var ruleMessageString = rule.flatMap(ReportingDescriptor::getMessageStrings).map(MessageStrings::getAdditionalProperties).map(strings -> strings.get(messageId));
        var globalMessageString = driver.getGlobalMessageStrings().map(GlobalMessageStrings::getAdditionalProperties).map(strings -> strings.get(messageId));

        var messageString = ruleMessageString.or(() -> globalMessageString).orElseThrow(() -> new SarifFormatException("Message lookup failed"));
        return messageString.getText();
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
