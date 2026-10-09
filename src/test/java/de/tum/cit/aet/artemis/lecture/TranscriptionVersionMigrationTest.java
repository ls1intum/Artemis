package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class TranscriptionVersionMigrationTest {

    @ParameterizedTest
    @ValueSource(strings = { "jdbc:tc:postgresql:18.4-alpine:///iris_version_upgrade", "jdbc:tc:mysql:8.4:///iris_version_upgrade" })
    void initializesOnlyExistingCompletedTranscriptionsWithoutChangingKnownVersions(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE lecture_unit_processing_state (lecture_unit_id BIGINT PRIMARY KEY, transcription_version INT, transcription_content_hash VARCHAR(64))");
            statement.execute("CREATE TABLE lecture_transcription (lecture_unit_id BIGINT PRIMARY KEY, transcription_status VARCHAR(32))");
            statement.execute("INSERT INTO lecture_unit_processing_state VALUES (1, NULL, NULL), (2, NULL, NULL), (3, 7, 'existing'), (4, NULL, NULL)");
            statement.execute("INSERT INTO lecture_transcription VALUES (1, 'COMPLETED'), (2, 'PENDING'), (3, 'COMPLETED'), (5, 'COMPLETED')");
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("config/liquibase/changelog/20261002172602_changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                try (var result = statement
                        .executeQuery("SELECT lecture_unit_id, transcription_version, transcription_content_hash FROM lecture_unit_processing_state ORDER BY lecture_unit_id")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("transcription_version")).isEqualTo(1);
                    assertThat(result.getString("transcription_content_hash")).isNull();
                    assertThat(result.next()).isTrue();
                    assertThat(result.getObject("transcription_version")).isNull();
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("transcription_version")).isEqualTo(7);
                    assertThat(result.getString("transcription_content_hash")).isEqualTo("existing");
                    assertThat(result.next()).isTrue();
                    assertThat(result.getObject("transcription_version")).isNull();
                    assertThat(result.next()).isFalse();
                }
            }
        }
    }
}
