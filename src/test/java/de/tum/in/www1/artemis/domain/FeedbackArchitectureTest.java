package de.tum.in.www1.artemis.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.lang.ArchRule;

import de.tum.in.www1.artemis.AbstractArchitectureTest;
import de.tum.in.www1.artemis.service.FeedbackService;

class FeedbackArchitectureTest extends AbstractArchitectureTest {

    @Test
    void testSetLongFeedbackTextNotUsed() {
        ArchRule toListUsage = noClasses().should().callMethod(Feedback.class, "setLongFeedbackText").because("this method should only be used by JPA. Use setDetailText instead.");
        toListUsage.check(allClasses);
        toListUsage.check(testClasses);
    }

    @Test
    void testGetLongFeedbackTextNotUsed() {
        ArchRule toListUsage = noClasses().that().areNotAssignableFrom(Feedback.class).and().areNotAssignableFrom(FeedbackTest.class).and()
                .areNotAssignableFrom(FeedbackService.class).should().callMethod(Feedback.class, "getLongFeedbackText")
                .because("this method should only be used by JPA. Use getLongFeedback instead.");
        toListUsage.check(allClasses);
        toListUsage.check(testClasses);
    }
}
