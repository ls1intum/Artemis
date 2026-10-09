package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Keeps websocket authorization declarative: every topic is declared once, together with who may subscribe, in the {@code *WebsocketTopics} class of its module, and
 * client messages only reach {@code @MessageMapping} handlers. See {@code documentation/docs/developer/guidelines/websocket.mdx}.
 */
class WebsocketTopicArchitectureTest extends AbstractArchitectureTest {

    @Test
    void topicsAreConstantsOfATopicProvider() {
        // the declaration types themselves, such as WebsocketDestination, hold a topic as an ordinary field
        fields().that().haveRawType(WebsocketTopic.class).or().haveRawType(WebsocketUserTopic.class).and().areDeclaredInClassesThat()
                .resideOutsideOfPackage(WebsocketTopic.class.getPackageName()).should().bePublic().andShould().beStatic().andShould().beFinal().andShould()
                .beDeclaredInClassesThat().implement(WebsocketTopicProvider.class)
                .because("the registry only knows the topics declared as constants of a WebsocketTopicProvider; a topic declared elsewhere cannot be subscribed to")
                .check(productionClasses);
    }

    @Test
    void topicProvidersAreLazyComponentsInTheWebPackage() {
        classes().that().implement(WebsocketTopicProvider.class).should().beAnnotatedWith(Component.class).andShould().beAnnotatedWith(Lazy.class).andShould()
                .haveSimpleNameEndingWith("WebsocketTopics").andShould().resideInAPackage("..web..")
                .because("each module declares its websocket topics in one <Module>WebsocketTopics class next to its REST resources").check(productionClasses);
    }

    @Test
    void onlyTheMessagingServiceTalksToTheBroker() {
        noClasses().that().doNotBelongToAnyOf(WebsocketMessagingService.class).should().dependOnClassesThat().areAssignableTo(SimpMessageSendingOperations.class)
                .because("WebsocketMessagingService only accepts destinations of declared topics, which is what makes every topic carry its access rule").check(productionClasses);
    }

    @Test
    void noSubscribeMappings() {
        noMethods().should().beAnnotatedWith(SubscribeMapping.class)
                .because("subscriptions go to the broker; react to a subscription with a SessionSubscribeEvent listener, and declare who may subscribe with the WebsocketTopic")
                .check(productionClasses);
    }

    @Test
    void messageHandlersDoNotPublishReturnValues() {
        methods().that().areAnnotatedWith(MessageMapping.class).should().haveRawReturnType(void.class)
                .because("a return value would be published straight to a broker destination, past the declared topics; send with WebsocketMessagingService instead")
                .check(productionClasses);
        noMethods().should().beAnnotatedWith(SendTo.class).orShould().beAnnotatedWith(SendToUser.class).because("messages go to declared topics through WebsocketMessagingService")
                .check(productionClasses);
    }

    @Test
    void messageMappingsAreApplicationDestinations() {
        methods().that().areAnnotatedWith(MessageMapping.class).should().beDeclaredInClassesThat().areAnnotatedWith(Controller.class).andShould(notMapBrokerDestinations())
                .because("clients send to /app/..., which only reaches @MessageMapping handlers; those handlers check the permissions of the sender themselves")
                .check(productionClasses);
    }

    /**
     * The registry refuses topics that overlap, but only when it is created, which in a test context only happens for the modules that are active there. This builds it
     * from the providers of all modules at once.
     */
    @Test
    void topicsOfAllModulesAreUnambiguous() throws ReflectiveOperationException {
        List<WebsocketTopicProvider> providers = new ArrayList<>();
        for (JavaClass providerClass : productionClasses
                .that(describe("topic providers", javaClass -> !javaClass.isInterface() && javaClass.isAssignableTo(WebsocketTopicProvider.class)))) {
            // the checks of the providers are not called here, so their dependencies can stay empty
            Constructor<?> constructor = providerClass.reflect().getDeclaredConstructors()[0];
            providers.add((WebsocketTopicProvider) constructor.newInstance(new Object[constructor.getParameterCount()]));
        }
        assertThat(providers).hasSizeGreaterThan(10);
        new WebsocketTopicRegistry(providers, mock(UserRepository.class), mock(ExerciseRepository.class), mock(ElevatedAccessService.class));
    }

    private static ArchCondition<JavaMethod> notMapBrokerDestinations() {
        return new ArchCondition<>("not map a broker destination") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (String value : method.getAnnotationOfType(MessageMapping.class).value()) {
                    String normalized = value.startsWith("/") ? value.substring(1) : value;
                    if (List.of("topic", "user", "app").stream().anyMatch(prefix -> normalized.equals(prefix) || normalized.startsWith(prefix + "/"))) {
                        events.add(violated(method, "%s maps %s, but mappings are relative to the application prefix /app".formatted(method.getFullName(), value)));
                    }
                }
            }
        };
    }
}
