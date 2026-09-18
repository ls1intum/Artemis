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

    private static final Pattern DROPPED_TABLE = Pattern.compile("<dropTable\\b[^>]*tableName=\"([^\"]+)\"");

    private static final Pattern DROPPED_COLUMN = Pattern.compile("<dropColumn\\b[^>]*tableName=\"([^\"]+)\"[^>]*columnName=\"([^\"]+)\"");

    /**
     * A rollback describes how to undo a changeset, not what the schema has. Reading it as if it did made the column a
     * changeset adds and its rollback drops look like a column that is not there.
     */
    private static final Pattern ROLLBACK = Pattern.compile("<rollback\\b[^>]*(?<!/)>.*?</rollback>", Pattern.DOTALL);

    /**
     * Every foreign key to {@code jhi_user} that the schema still has needs exactly one policy, and no policy may
     * name a reference the schema no longer has.
     *
     * <p>
     * The changelog is a history rather than a picture of the schema: a table that has since been dropped keeps the
     * changeset that created its foreign key. Reading the additions alone therefore demanded a policy for
     * {@code user_groups} and {@code competency_jol} long after both tables were gone, and the deletion had to look
     * up at run time which of its tables still existed in order to skip them again. Subtracting what later
     * changesets drop is what lets the catalogue mean the current schema.
     *
     * <p>
     * The walk starts at the Liquibase root rather than at one directory below it, so that it covers the
     * baseline, the folded history and the changelogs written since alike. Where a changelog sits is a
     * question of what has been consolidated, which says nothing about whether it declares a foreign key.
     */
    @Test
    void everyLiquibaseForeignKeyToUserHasExactlyOnePolicy() throws IOException {
        Set<String> schemaReferences = new HashSet<>();
        Set<String> droppedTables = new HashSet<>();
        Set<String> droppedColumns = new HashSet<>();
        Path changelogDirectory = Path.of("src/main/resources/config/liquibase");
        try (Stream<Path> paths = Files.walk(changelogDirectory)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".xml")).toList()) {
                String changelog = ROLLBACK.matcher(Files.readString(path)).replaceAll("");
                Matcher matcher = USER_FOREIGN_KEY.matcher(changelog);
                while (matcher.find()) {
                    schemaReferences.add(attribute(BASE_TABLE, matcher.group(1)) + "." + attribute(BASE_COLUMN, matcher.group(1)));
                }
                Matcher droppedTable = DROPPED_TABLE.matcher(changelog);
                while (droppedTable.find()) {
                    droppedTables.add(droppedTable.group(1));
                }
                Matcher droppedColumn = DROPPED_COLUMN.matcher(changelog);
                while (droppedColumn.find()) {
                    droppedColumns.add(droppedColumn.group(1) + "." + droppedColumn.group(2));
                }
            }
        }
        schemaReferences.removeIf(reference -> droppedTables.contains(reference.substring(0, reference.indexOf('.'))) || droppedColumns.contains(reference));

        assertThat(UserDeletionReferencePolicy.byReferenceKey().keySet()).containsExactlyInAnyOrderElementsOf(schemaReferences);
    }

    @Test
    void authoredExerciseAndSubmissionVersionsAreDeletedWithTheirAuthor() {
        assertThat(UserDeletionReferencePolicy.EXERCISE_VERSION_AUTHOR.action()).isEqualTo(UserDeletionAction.DELETE);
        assertThat(UserDeletionReferencePolicy.SUBMISSION_VERSION_AUTHOR.action()).isEqualTo(UserDeletionAction.DELETE);
    }

    private String attribute(Pattern pattern, String attributes) {
        Matcher matcher = pattern.matcher(attributes);
        assertThat(matcher.find()).as("required foreign-key attribute in %s", attributes).isTrue();
        return matcher.group(1);
    }
}
