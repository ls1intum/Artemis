package de.tum.in.www1.artemis.notification;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NotificationPlaceholderSignatureTest {

    /**
     * If this test fails you have changed the notification placeholder files. This may have dramatic consequences on the mobile applications (iOS & Android) and the database.
     * Changing the notification placeholders files changes the following things:
     * 1. Other key value pairs are sent to the clients. This may mean that the native clients no longer understand them and notifications are no longer shown to users.
     * 2. The database becomes inconsistent. The placeholders are stored as Json in the database.
     * You must now do the following:
     * 1. Check if you really need to change these placeholders. If not, revert your changes.
     * 2. Write a database migration for the old placeholder JSON strings, such that they match your new signature.
     * 3. Increment the {{@link de.tum.in.www1.artemis.config.Constants#PUSH_NOTIFICATION_VERSION}}. This ensures that old versions of the native apps discard your new
     * notifications.
     * 4. !!!!! Update both the Android and iOS app. Only merge this server PR after they have been updated and released to the stores. Otherwise, notifications no longer work for
     * end users. !!!!
     * 5. Execute the Gradle task generatePlaceholderRevisionSignatures to make this test pass again.
     */
    @Test
    public void testSignatureHasNotChanged() throws URISyntaxException, IOException {
        var classLoader = NotificationPlaceholderSignatureTest.class.getClassLoader();
        var expectedSignature = readPlaceholderText(Objects.requireNonNull(classLoader.getResource("expected-placeholder-signatures.json")));
        var actualSignature = readPlaceholderText(Objects.requireNonNull(classLoader.getResource("placeholder-signatures.json")));

        Assertions.assertEquals(expectedSignature, actualSignature);
    }

    private String readPlaceholderText(URL url) throws URISyntaxException, IOException {
        return Files.readString(Paths.get(url.toURI()));
    }
}
