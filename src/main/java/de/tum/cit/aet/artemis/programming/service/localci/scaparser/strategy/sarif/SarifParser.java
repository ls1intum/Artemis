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
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Level;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Location;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.MessageStrings;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.PhysicalLocation;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Region;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingConfiguration;
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

    private record FileLocation(String path, int startLine, int endLine, int startColumn, int endColumn) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StaticCodeAnalysisTool tool;

    private final RuleCategorizer ruleCategorizer;

    private final MessageProcessor messageProcessor;

    public SarifParser(StaticCodeAnalysisTool tool, RuleCategorizer ruleCategorizer) {
        this(tool, ruleCategorizer, null);
    }

    public SarifParser(StaticCodeAnalysisTool tool, RuleCategorizer ruleCategorizer, MessageProcessor messageProcessor) {
        this.tool = tool;
        this.ruleCategorizer = ruleCategorizer;
        this.messageProcessor = messageProcessor;
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

        Run run = sarifLog.runs().getFirst();
        ToolComponent driver = run.tool().driver();

        List<ReportingDescriptor> rules = driver.getOptionalRules().orElse(List.of());

        // Rule ids are not guaranteed to be unique. Use the first occurring for rule lookup.
        Map<String, ReportingDescriptor> ruleOfId = rules.stream().collect(Collectors.toMap(ReportingDescriptor::id, Function.identity(), (first, next) -> first));

        List<Result> results = run.getOptionalResults().orElse(List.of());
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
        FileLocation fileLocation = result.getOptionalLocations().flatMap(locations -> locations.stream().findFirst()).flatMap(Location::getOptionalPhysicalLocation)
                .map(this::extractLocation).orElseThrow(() -> new InformationMissingException("Location needed"));

        String ruleId = getRuleId(result);

        Optional<Integer> ruleIndex = getRuleIndex(result);

        Optional<ReportingDescriptor> ruleByIndex = driver.getOptionalRules().flatMap(rules -> ruleIndex.map(rules::get));
        Optional<ReportingDescriptor> rule = ruleByIndex.or(() -> lookupRuleById(ruleId, ruleOfId));

        String category = ruleCategorizer.categorizeRule(rule.orElseGet(() -> new ReportingDescriptor(ruleId)));

        Level level = result.getOptionalLevel().orElseGet(() -> getDefaultLevel(rule));

        String rawMessage = findMessage(result, driver, rule);
        String message = messageProcessor != null ? messageProcessor.processMessage(rawMessage, rule.orElse(null)) : rawMessage;

        return new StaticCodeAnalysisIssue(fileLocation.path(), fileLocation.startLine(), fileLocation.endLine(), fileLocation.startColumn(), fileLocation.endColumn(), ruleId,
                category, message, level.toString(), null);
    }

    private FileLocation extractLocation(PhysicalLocation location) {
        URI uri = URI
                .create(location.getOptionalArtifactLocation().flatMap(ArtifactLocation::getOptionalUri).orElseThrow(() -> new InformationMissingException("File path needed")));

        Region region = location.getOptionalRegion().orElseThrow(() -> new SarifFormatException("Region must be present"));

        int startLine = region.getOptionalStartLine().orElseThrow(() -> new InformationMissingException("Text region needed"));
        int startColumn = region.getOptionalStartColumn().orElse(1);
        int endLine = region.getOptionalEndLine().orElse(startLine);
        int endColumn = region.getOptionalEndColumn().orElse(startColumn + 1);

        return new FileLocation(uri.getPath(), startLine, endLine, startColumn, endColumn);
    }

    private static String getRuleId(Result result) throws SarifFormatException {
        return result.getOptionalRuleId().orElseGet(() -> result.getOptionalRule().flatMap(ReportingDescriptorReference::getOptionalId)
                .orElseThrow(() -> new SarifFormatException("Either ruleId or rule.id must be present")));
    }

    private static Optional<Integer> getRuleIndex(Result result) {
        // ruleIndex can use -1 to indicate a missing value
        Optional<Integer> ruleIndexOrMinusOne = result.getOptionalRuleIndex().or(() -> result.getOptionalRule().flatMap(ReportingDescriptorReference::getOptionalIndex));
        return ruleIndexOrMinusOne.flatMap(index -> index != -1 ? Optional.of(index) : Optional.empty());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String findMessage(Result result, ToolComponent driver, Optional<ReportingDescriptor> rule) throws SarifFormatException {
        return result.message().getOptionalText().orElseGet(() -> lookupMessageById(result, driver, rule));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String lookupMessageById(Result result, ToolComponent driver, Optional<ReportingDescriptor> rule) throws SarifFormatException {
        String messageId = result.message().getOptionalId().orElseThrow(() -> new SarifFormatException("Either text or id must be present"));

        var ruleMessageString = rule.flatMap(ReportingDescriptor::getOptionalMessageStrings).map(MessageStrings::additionalProperties).map(strings -> strings.get(messageId));
        var globalMessageString = driver.getOptionalGlobalMessageStrings().map(GlobalMessageStrings::additionalProperties).map(strings -> strings.get(messageId));

        var messageString = ruleMessageString.or(() -> globalMessageString).orElseThrow(() -> new SarifFormatException("Message lookup failed"));
        return messageString.text();
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

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Level getDefaultLevel(Optional<ReportingDescriptor> rule) {
        return rule.flatMap(ReportingDescriptor::getOptionalDefaultConfiguration).flatMap(ReportingConfiguration::getOptionalLevel).orElse(Level.WARNING);
    }
}
