package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class UserDeletionReferencePolicyTest {

    private static final Pattern USER_FOREIGN_KEY = Pattern.compile("<addForeignKeyConstraint\\b([^>]*referencedTableName=\"jhi_user\"[^>]*)/?>", Pattern.DOTALL);

    private static final Pattern BASE_TABLE = Pattern.compile("baseTableName=\"([^\"]+)\"");

    private static final Pattern BASE_COLUMN = Pattern.compile("baseColumnNames=\"([^\"]+)\"");

    @Test
    void everyLiquibaseForeignKeyToUserHasExactlyOnePolicy() throws IOException {
        Set<String> schemaReferences = new HashSet<>();
        Path changelogDirectory = Path.of("src/main/resources/config/liquibase/changelog");
        try (Stream<Path> paths = Files.walk(changelogDirectory)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".xml")).toList()) {
                Matcher matcher = USER_FOREIGN_KEY.matcher(Files.readString(path));
                while (matcher.find()) {
                    schemaReferences.add(attribute(BASE_TABLE, matcher.group(1)) + "." + attribute(BASE_COLUMN, matcher.group(1)));
                }
            }
        }

        assertThat(UserDeletionReferencePolicy.byReferenceKey().keySet()).containsExactlyInAnyOrderElementsOf(schemaReferences);
    }

    private String attribute(Pattern pattern, String attributes) {
        Matcher matcher = pattern.matcher(attributes);
        assertThat(matcher.find()).as("required foreign-key attribute in %s", attributes).isTrue();
        return matcher.group(1);
    }
}
