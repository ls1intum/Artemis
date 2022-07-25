package de.tum.in.www1.artemis.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.config.migration.MigrationIntegrityException;
import de.tum.in.www1.artemis.config.migration.MigrationRegistry;
import de.tum.in.www1.artemis.config.migration.MigrationService;
import de.tum.in.www1.artemis.domain.MigrationChangelog;
import de.tum.in.www1.artemis.migration.entries.TestChangeEntry20211214_231800;
import de.tum.in.www1.artemis.migration.entries.TestChangeEntry20211215_231800;
import de.tum.in.www1.artemis.migration.entries.TestChangeEntry20211216_231800;
import de.tum.in.www1.artemis.repository.MigrationChangeRepository;

class MigrationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MigrationRegistry registry;

    @Autowired
    private MigrationChangeRepository migrationChangeRepository;

    @Autowired
    private MigrationService migrationService;

    private ApplicationReadyEvent applicationReadyEventMock;

    private ConfigurableEnvironment environmentMock;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        applicationReadyEventMock = mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext configurableApplicationContextMock = mock(ConfigurableApplicationContext.class);
        environmentMock = mock(ConfigurableEnvironment.class);
        when(applicationReadyEventMock.getApplicationContext()).thenReturn(configurableApplicationContextMock);
        when(configurableApplicationContextMock.getEnvironment()).thenReturn(environmentMock);
        when(environmentMock.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))).thenReturn(false);
    }

    @AfterEach
    void teardown() {
        database.resetDatabase();
    }

    @Test
    void testValidMap() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211214_231800", "Valid Author"));
        map.put(1, createEntryMock("20211215_231800", "Valid Author"));
        map.put(2, createEntryMock("20211216_231800", "Valid Author"));

        assertThat(migrationService.checkIntegrity(map)).isTrue();
        assertThat(migrationService.checkIntegrity(new TreeMap<>())).isTrue();
    }

    @Test
    void testBrokenEntriesFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211216_231800", null));
        assertThat(migrationService.checkIntegrity(map)).isFalse();

        map.clear();
        map.put(0, createEntryMock("20211216_231800", ""));
        assertThat(migrationService.checkIntegrity(map)).isFalse();

        map.clear();
        map.put(0, createEntryMock(null, "Valid Author"));
        assertThat(migrationService.checkIntegrity(map)).isFalse();

        map.clear();
        map.put(0, createEntryMock("", "Valid Author"));
        assertThat(migrationService.checkIntegrity(map)).isFalse();
    }

    @Test
    void testDuplicateDateStringsFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211216_231800", "Valid Author"));
        map.put(1, createEntryMock("20211216_231800", "Valid Author"));
        assertThat(migrationService.checkIntegrity(map)).isFalse();
    }

    @Test
    void testWrongOrderDateStringsFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211217_231800", "Valid Author"));
        map.put(1, createEntryMock("20211216_231800", "Valid Author"));
        assertThat(migrationService.checkIntegrity(map)).isFalse();
    }

    @Test
    void testStartDateEmptyFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock(null, "Valid Author"));
        map.put(1, createEntryMock("", "Valid Author"));
        map.put(2, createEntryMock("20211216_231800", "Valid Author"));
        map.put(3, createEntryMock("20211217_231800", "Valid Author"));
        assertThat(migrationService.checkIntegrity(map)).isFalse();
    }

    @Test
    void testNoExecutionOnTestEnvironment() throws IOException, NoSuchAlgorithmException, MigrationIntegrityException {
        reset(environmentMock);
        when(environmentMock.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))).thenReturn(true);

        registry.execute(applicationReadyEventMock);

        assertThat(migrationChangeRepository.findAll()).isEmpty();
    }

    @Test
    void testStopApplicationOnFailedIntegrityCheck() {
        assertThatThrownBy(() -> migrationService.execute(applicationReadyEventMock, createInvalidChangelog())).isInstanceOf(MigrationIntegrityException.class);
        assertThat(migrationChangeRepository.findAll()).isEmpty();
    }

    @Test
    void testExecutionSucceedsForEmptyMap() throws MigrationIntegrityException {
        migrationService.execute(applicationReadyEventMock, new TreeMap<>());

        assertThat(migrationChangeRepository.findAll()).isEmpty();
    }

    @Test
    void testExecutionSucceedsForRegularMap() throws MigrationIntegrityException {
        migrationService.execute(applicationReadyEventMock, createValidChangelog());

        List<MigrationChangelog> changelogs = migrationChangeRepository.findAll();
        assertThat(changelogs).hasSize(3);
        assertThat(changelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);
    }

    @Test
    void testExecutionOnMultipleStarts() throws MigrationIntegrityException {
        migrationService.execute(applicationReadyEventMock, createValidChangelog());

        List<MigrationChangelog> changelogs = migrationChangeRepository.findAll();
        assertThat(changelogs).hasSize(3);
        assertThat(changelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);

        migrationService.execute(applicationReadyEventMock, createValidChangelog());

        List<MigrationChangelog> secondChangelogs = migrationChangeRepository.findAll();
        assertThat(secondChangelogs).hasSize(3);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);
    }

    @Test
    void testSplitExecution() throws MigrationIntegrityException {
        SortedMap<Integer, Class<? extends MigrationEntry>> changelog = new TreeMap<>();
        changelog.put(0, TestChangeEntry20211214_231800.class);
        changelog.put(1, TestChangeEntry20211215_231800.class);

        migrationService.execute(applicationReadyEventMock, changelog);

        List<MigrationChangelog> changelogs = migrationChangeRepository.findAll();
        assertThat(changelogs).hasSize(2);
        assertThat(changelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(2);

        changelog.put(2, TestChangeEntry20211216_231800.class);

        migrationService.execute(applicationReadyEventMock, createValidChangelog());

        List<MigrationChangelog> secondChangelogs = migrationChangeRepository.findAll();
        assertThat(secondChangelogs).hasSize(3);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(2);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);
    }

    private SortedMap<Integer, Class<? extends MigrationEntry>> createValidChangelog() {
        SortedMap<Integer, Class<? extends MigrationEntry>> map = new TreeMap<>();
        map.put(0, TestChangeEntry20211214_231800.class);
        map.put(1, TestChangeEntry20211215_231800.class);
        map.put(2, TestChangeEntry20211216_231800.class);
        return map;
    }

    private SortedMap<Integer, Class<? extends MigrationEntry>> createInvalidChangelog() {
        SortedMap<Integer, Class<? extends MigrationEntry>> map = new TreeMap<>();
        map.put(0, TestChangeEntry20211214_231800.class);
        map.put(1, TestChangeEntry20211216_231800.class);
        map.put(2, TestChangeEntry20211215_231800.class);
        return map;
    }

    private MigrationEntry createEntryMock(String date, String author) {
        MigrationEntry mock = mock(MigrationEntry.class);
        when(mock.date()).thenReturn(date);
        when(mock.author()).thenReturn(author);
        return mock;
    }
}
