package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableFrom;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.FeedbackService;

class FeedbackArchitectureTest extends AbstractArchitectureTest {

    @Test
    void testSetLongFeedbackTextNotUsed() {
        final ArchRule setLongFeedbackTextUsage = noClasses().should().callMethod(Feedback.class, "setLongFeedbackText")
                .because("this method should only be used by JPA. Use setDetailText instead.");
        setLongFeedbackTextUsage.check(allClasses);
    }

    @Test
    void testGetLongFeedbackTextNotUsed() {
        final ArchRule getLongFeedbackTextUsage = noClasses().should().callMethod(Feedback.class, "getLongFeedbackText")
                .because("this method should only be used by JPA. Use getLongFeedback instead.");

        // internal usage for the Feedback and its tests okay, the service needs it for Hibernate lazy initialisation checks
        final JavaClasses classesToCheck = allClasses
                .that(are(not(assignableFrom(Feedback.class).or(assignableFrom("de.tum.in.www1.artemis.domain.FeedbackTest")).or(assignableFrom(FeedbackService.class)))));

        getLongFeedbackTextUsage.check(classesToCheck);
    }
}
