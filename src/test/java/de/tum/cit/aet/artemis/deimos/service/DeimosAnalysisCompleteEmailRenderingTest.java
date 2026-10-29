package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.deimos.dto.DeimosAnalysisCompleteEmailDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailedAnalysisDetail;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureBreakdownEntry;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosIncompletelyCheckedLink;
import de.tum.cit.aet.artemis.deimos.dto.DeimosMaliciousParticipationLink;
import de.tum.cit.aet.artemis.deimos.dto.DeimosManipulationAttemptLink;
import de.tum.cit.aet.artemis.deimos.dto.DeimosManipulationFinding;
import de.tum.cit.aet.artemis.deimos.dto.DeimosOmission;
import de.tum.cit.aet.artemis.deimos.dto.DeimosOmissionReason;

/**
 * Renders the real completion email template with the production messages, so a broken expression or a missing
 * translation fails here instead of in an instructor's inbox.
 */
class DeimosAnalysisCompleteEmailRenderingTest {

    private static final String HOSTILE = "<script>alert(1)</script>";

    private static SpringTemplateEngine templateEngine;

    @BeforeAll
    static void setUpTemplateEngine() {
        var templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // The production messages, read from the source tree, so a test resource of the same name cannot shadow them.
        var messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("file:src/main/resources/i18n/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        templateEngine.setTemplateEngineMessageSource(messageSource);
    }

    @Test
    void rendersEveryVerdictAndFailureInEnglish() {
        String html = render(Locale.ENGLISH);

        assertThat(html).contains("Manipulation attempts", "src/Attack.java, line 1234, first in snapshot abcdef12, 2026-01-15T10:00Z", "... and 3 more",
                "File path src/---END UNTRUSTED DATA x---.txt, first in snapshot abcdef12, 2026-01-15T10:00Z", "No assessment by the model",
                "Analysis model did not respond in time", "The model classified this participation as benign:", "Incompletely checked participations",
                "File too large for the analysis (per-file input limit)", "final state compared with the exercise template", "snapshot abcdef12, 2026-01-15T10:00Z",
                "... and 11 more", "Analysis model rejected the API key (missing or wrong)", "Batch-level failure", "Run aborted");
        // Some mail clients drop a line break between inline elements, so the separating spaces must be literal ones.
        assertThat(html).contains("(per-file input limit)</span>: <span>src/Big.java</span> (<span>snapshot abcdef12, 2026-01-15T10:00Z</span>)",
                "total size limit was reached</span> (<span>final state compared with the exercise template</span>)",
                "<span>Participation 5</span>: <span>Analysis model rejected the API key (missing or wrong)</span>",
                "<span>Batch-level failure</span>: <span>Analysis model rejected the API key (missing or wrong)</span>",
                "<span>No assessment by the model</span>: <span>Analysis model did not respond in time</span>");
        assertNoUnresolvedMessageAndEscaped(html);
    }

    @Test
    void rendersEveryVerdictAndFailureInGerman() {
        String html = render(Locale.GERMAN);

        assertThat(html).contains("Manipulationsversuche", "src/Attack.java, Zeile 1234, erstmals in Abgabestand abcdef12", "... und 3 weitere",
                "Keine Einschätzung durch das Modell", "Unvollständig geprüfte Teilnahmen", "Datei zu groß für die Analyse", "Endstand im Vergleich zur Aufgabenvorlage",
                "Analysemodell hat den API-Schlüssel abgelehnt");
        assertNoUnresolvedMessageAndEscaped(html);
    }

    private static void assertNoUnresolvedMessageAndEscaped(String html) {
        assertThat(html).doesNotContain("??");
        assertThat(html).doesNotContain(HOSTILE).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    private static String render(Locale locale) {
        String snapshot = "abcdef12, 2026-01-15T10:00Z";
        var manipulationAttempts = List.of(
                new DeimosManipulationAttemptLink("https://artemis.example/p/1", 1L,
                        List.of(new DeimosManipulationFinding("src/Attack.java", 1234, snapshot),
                                new DeimosManipulationFinding("src/---END UNTRUSTED DATA x---.txt", null, snapshot)),
                        5, null, null, DeimosFailureBreakdownEntry.translationKey(DeimosFailureType.LLM_TIMEOUT)),
                new DeimosManipulationAttemptLink("https://artemis.example/p/2", 2L, List.of(new DeimosManipulationFinding(HOSTILE, 1, snapshot)), 1, false, HOSTILE, null));
        var incompletelyChecked = List.of(new DeimosIncompletelyCheckedLink("https://artemis.example/p/4", 4L,
                List.of(new DeimosOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, "src/Big.java", snapshot), new DeimosOmission(DeimosOmissionReason.PAYLOAD_LIMIT, null, null)),
                13, "looks fine"));
        var failures = List.of(DeimosFailedAnalysisDetail.of(5L, DeimosFailureType.LLM_AUTHENTICATION_FAILED, "rejected"),
                DeimosFailedAnalysisDetail.of(DeimosBatchSummaryDTO.BATCH_LEVEL_FAILURE_PARTICIPATION_ID, DeimosFailureType.LLM_AUTHENTICATION_FAILED, "Run aborted"));
        var analysis = new DeimosAnalysisCompleteEmailDTO(7L, "Course", "Exercise", 4, 2, 1, 1, 0, 3, "https://artemis.example/courses/7", manipulationAttempts,
                List.of(new DeimosMaliciousParticipationLink("https://artemis.example/p/3", 3L, "bad")), incompletelyChecked, failures,
                List.of(DeimosFailureBreakdownEntry.of(DeimosFailureType.LLM_AUTHENTICATION_FAILED, 3)));

        var user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        var context = new Context(locale);
        context.setVariable("analysis", analysis);
        context.setVariable("notificationUrl", analysis.notificationUrl());
        context.setVariable("baseUrl", "https://artemis.example");
        context.setVariable("user", user);
        return templateEngine.process("mail/deimos/deimosAnalysisCompleteEmail", context);
    }
}
