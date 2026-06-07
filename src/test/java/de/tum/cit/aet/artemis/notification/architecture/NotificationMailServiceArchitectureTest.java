package de.tum.cit.aet.artemis.notification.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.lang.ArchRule;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Architecture tests enforcing that the mail-related services in the notification module operate purely on DTOs and do
 * not depend on JPA {@code @Entity} domain classes (which all reside in {@code ..domain..} packages).
 * <p>
 * Passing domain entities into the mail layer couples mail rendering to the persistence context and risks
 * {@code LazyInitializationException}s when templates dereference lazy associations on the asynchronous mail thread.
 * The mail services therefore take {@link de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO} (and other DTOs)
 * instead, and callers extract the required scalar values up-front.
 */
class NotificationMailServiceArchitectureTest extends AbstractArchitectureTest {

    // Exact package (no trailing "..") so the rule covers the services directly in 'notification.service.notifications'
    // but not the 'push_notifications' subpackage, whose native-push services legitimately operate on device/user entities.
    private static final String NOTIFICATIONS_SERVICE_PACKAGE = ARTEMIS_PACKAGE + ".notification.service.notifications";

    private static final String DOMAIN_PACKAGE = "..domain..";

    /**
     * The mail rendering ({@code MailService}) and mail sending ({@code MailSendingService}) services must never depend
     * on a domain entity.
     */
    @Test
    void mailServicesMustNotDependOnDomainClasses() {
        ArchRule rule = noClasses().that().haveSimpleName("MailService").or().haveSimpleName("MailSendingService").should().dependOnClassesThat().resideInAPackage(DOMAIN_PACKAGE)
                .because("the mail services must operate on DTOs (e.g. MailRecipientDTO), not on JPA domain entities, to avoid coupling mail rendering to the persistence context");
        rule.check(productionClasses);
    }

    /**
     * Extends the rule to the other services directly in {@code notification.service.notifications} (mail services and
     * the markdown rendering helpers). The three notification orchestrators are excluded for now: they legitimately
     * consume domain entities (e.g. {@code Exercise}, {@code User}) to build notifications and cannot be decoupled
     * without a much larger refactoring. The {@code push_notifications} subpackage is out of scope for the same reason.
     * <p>
     * TODO: migrate {@code GroupNotificationService}, {@code SingleUserNotificationService} and
     * {@code GroupNotificationScheduleService} to DTO inputs and remove them from this exclusion.
     */
    @Test
    void notificationServicesMustNotDependOnDomainClasses() {
        ArchRule rule = noClasses().that().resideInAPackage(NOTIFICATIONS_SERVICE_PACKAGE).and()
                .haveNameNotMatching(".*\\.(GroupNotificationService|SingleUserNotificationService|GroupNotificationScheduleService)(\\$.*)?").should().dependOnClassesThat()
                .resideInAPackage(DOMAIN_PACKAGE)
                .because("services in 'notification.service.notifications' should operate on DTOs rather than JPA domain entities (notification orchestrators excluded for now)");
        rule.check(productionClasses);
    }
}
