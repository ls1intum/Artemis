package de.tum.in.www1.artemis.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import io.github.classgraph.AnnotationEnumValue;
import io.github.classgraph.ClassGraph;

class NotificationPlaceholderSignatureTest extends AbstractSpringIntegrationIndependentTest {

    /**
     * If this test fails, you have changed the notification placeholder files. This may have dramatic consequences on the mobile applications (iOS & Android) and the database.
     * Changing the notification placeholders files changes the following things:
     * 1. Other key value pairs are sent to the clients. This may mean that the native clients no longer understand them and notifications are no longer shown to users.
     * 2. The database becomes inconsistent. The placeholders are stored as JSON in the database.
     * You must now do the following:
     * 1. Check if you really need to change these placeholders. If not, revert your changes.
     * 2. Write a database migration for the old placeholder JSON strings, such that they match your new signature.
     * 3. Increment the {{@link de.tum.in.www1.artemis.config.Constants#PUSH_NOTIFICATION_VERSION}}. This ensures that old versions of the native apps discard your new
     * notifications.
     * 4. Update both the Android and iOS app. Only merge this server PR after they have been updated and released to the stores. Otherwise, notifications no longer work for
     * end users.
     * 5. Execute the Gradle task generatePlaceholderRevisionSignatures to make this test pass again.
     */
    @Test
    void testSignatureHasNotChanged() throws URISyntaxException, IOException {
        try (var scanResult = new ClassGraph().acceptPackages("de.tum.in.www1.artemis").enableAllInfo().scan()) {
            // Find the classes that are annotated as a notification placeholder file.
            var classes = scanResult.getClassesWithMethodAnnotation(NotificationPlaceholderCreator.class);

            // Create a signature for each annotated file.
            var signatures = classes.stream().flatMap(x -> x.getDeclaredMethodInfo().stream()).filter(method -> method.hasAnnotation(NotificationPlaceholderCreator.class))
                    .flatMap(placeholderCreatorMethod -> {
                        var fieldDescriptions = Arrays.stream(placeholderCreatorMethod.getParameterInfo())
                                .map(param -> new FieldDescription(param.getName(), param.getTypeDescriptor().toString())).toList();

                        var notificationTypes = (Object[]) placeholderCreatorMethod.getAnnotationInfo(NotificationPlaceholderCreator.class).getParameterValues().get("values")
                                .getValue();

                        return Arrays.stream(notificationTypes)
                                .map(notificationType -> new ClassSignature(((AnnotationEnumValue) notificationType).getValueName(), fieldDescriptions));
                    }).sorted().toList();

            // Signature as JSON.
            var actualSignature = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(signatures);
            var expectedSignature = readPlaceholderText(
                    Objects.requireNonNull(NotificationPlaceholderSignatureTest.class.getClassLoader().getResource("placeholder-signatures.json")));

            assertThat(expectedSignature).isEqualTo(actualSignature);
        }
    }

    private String readPlaceholderText(URL url) throws URISyntaxException, IOException {
        return Files.readString(Paths.get(url.toURI()));
    }

    private record ClassSignature(String notificationType, List<NotificationPlaceholderSignatureTest.FieldDescription> fieldDescriptions) implements Comparable<ClassSignature> {

        @Override
        public int compareTo(ClassSignature classSignature) {
            return notificationType.compareTo(classSignature.notificationType);
        }
    }

    private record FieldDescription(String fieldName, String fieldType) {
    }
}
