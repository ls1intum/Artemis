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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.config.migration.MigrationRegistry;
import de.tum.in.www1.artemis.domain.MigrationChangelog;
import de.tum.in.www1.artemis.migration.entries.TestChangeEntry20211214_231800;
import de.tum.in.www1.artemis.migration.entries.TestChangeEntry20211215_231800;
import de.tum.in.www1.artemis.migration.entries.TestChangeEntry20211216_231800;
import de.tum.in.www1.artemis.repository.MigrationChangeRepository;

public class MigrationIntegrityTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MigrationRegistry registrySpyMock;

    @Autowired
    private MigrationChangeRepository migrationChangeRepository;

    private ApplicationReadyEvent applicationReadyEventMock;

    private ConfigurableEnvironment environmentMock;

    private SecurityManager oldSecurityManager = null;

    private final SortedMap<Integer, Class<? extends MigrationEntry>> testEntryMap = new TreeMap<>();

    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException {
        applicationReadyEventMock = mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext configurableApplicationContextMock = mock(ConfigurableApplicationContext.class);
        environmentMock = mock(ConfigurableEnvironment.class);
        when(applicationReadyEventMock.getApplicationContext()).thenReturn(configurableApplicationContextMock);
        when(configurableApplicationContextMock.getEnvironment()).thenReturn(environmentMock);
        when(environmentMock.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))).thenReturn(false);
        this.oldSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new NoExitSecurityManager());

        testEntryMap.clear();
        testEntryMap.put(0, TestChangeEntry20211214_231800.class);
        testEntryMap.put(1, TestChangeEntry20211215_231800.class);
        testEntryMap.put(2, TestChangeEntry20211216_231800.class);

        registrySpyMock = spy(registrySpyMock);
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
        System.setSecurityManager(oldSecurityManager);
    }

    @Test
    public void testIntegrity() {
        registrySpyMock.instantiateEntryMap();

        assertThat(registrySpyMock.checkIntegrity()).as("Migration Integrity Check")
                .withFailMessage("The Migration integrity check was not successful. Check the logs for errors in the registry.").isTrue();
    }

    @Test
    public void testValidMap() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211214_231800", "Valid Author"));
        map.put(1, createEntryMock("20211215_231800", "Valid Author"));
        map.put(2, createEntryMock("20211216_231800", "Valid Author"));

        assertThat(registrySpyMock.checkIntegrity(map)).isTrue();
        assertThat(registrySpyMock.checkIntegrity(new TreeMap<>())).isTrue();
    }

    @Test
    public void testBrokenEntriesFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211216_231800", null));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();

        map.clear();
        map.put(0, createEntryMock("20211216_231800", ""));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();

        map.clear();
        map.put(0, createEntryMock(null, "Valid Author"));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();

        map.clear();
        map.put(0, createEntryMock("", "Valid Author"));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();
    }

    @Test
    public void testDuplicateDateStringsFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211216_231800", "Valid Author"));
        map.put(1, createEntryMock("20211216_231800", "Valid Author"));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();
    }

    @Test
    public void testWrongOrderDateStringsFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock("20211217_231800", "Valid Author"));
        map.put(1, createEntryMock("20211216_231800", "Valid Author"));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();
    }

    @Test
    public void testStartDateEmptyFail() {
        SortedMap<Integer, MigrationEntry> map = new TreeMap<>();
        map.put(0, createEntryMock(null, "Valid Author"));
        map.put(1, createEntryMock("", "Valid Author"));
        map.put(2, createEntryMock("20211216_231800", "Valid Author"));
        map.put(3, createEntryMock("20211217_231800", "Valid Author"));
        assertThat(registrySpyMock.checkIntegrity(map)).isFalse();
    }

    @Test
    public void testNoExecutionOnTestEnvironment() throws IOException, NoSuchAlgorithmException {
        Mockito.reset(environmentMock);
        when(environmentMock.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))).thenReturn(true);

        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(0)).instantiateEntryMap();

        assertThat(migrationChangeRepository.findAll()).hasSize(0);
    }

    @Test
    public void testStopApplicationOnFailedIntegrityCheck() {
        doReturn(false).when(registrySpyMock).checkIntegrity();
        doAnswer(invocation -> testEntryMap).when(registrySpyMock).getMigrationEntryMap();

        assertThatThrownBy(() -> registrySpyMock.execute(applicationReadyEventMock)).isInstanceOf(ExitException.class).hasFieldOrPropertyWithValue("status", 1);

        verify(registrySpyMock, times(1)).instantiateEntryMap();

        assertThat(migrationChangeRepository.findAll()).hasSize(0);
    }

    @Test
    public void testExecutionSucceedsForEmptyMap() throws IOException, NoSuchAlgorithmException {
        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(1)).checkIntegrity();

        assertThat(migrationChangeRepository.findAll()).hasSize(0);
    }

    @Test
    public void testExecutionSucceedsForRegularMap() throws IOException, NoSuchAlgorithmException {
        doAnswer(invocation -> testEntryMap).when(registrySpyMock).getMigrationEntryMap();

        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(1)).checkIntegrity();

        List<MigrationChangelog> changelogs = migrationChangeRepository.findAll();
        assertThat(changelogs).hasSize(testEntryMap.size());
        assertThat(changelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);
    }

    @Test
    public void testExecutionOnMultipleStarts() throws IOException, NoSuchAlgorithmException {
        doAnswer(invocation -> testEntryMap).when(registrySpyMock).getMigrationEntryMap();

        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(1)).checkIntegrity();

        List<MigrationChangelog> changelogs = migrationChangeRepository.findAll();
        assertThat(changelogs).hasSize(3);
        assertThat(changelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);

        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(2)).checkIntegrity();

        List<MigrationChangelog> secondChangelogs = migrationChangeRepository.findAll();
        assertThat(secondChangelogs).hasSize(3);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);
    }

    @Test
    public void testSplitExecution() throws IOException, NoSuchAlgorithmException {
        SortedMap<Integer, Class<? extends MigrationEntry>> changelog = new TreeMap<>();
        changelog.put(0, TestChangeEntry20211214_231800.class);
        changelog.put(1, TestChangeEntry20211215_231800.class);
        doAnswer(invocation -> changelog).when(registrySpyMock).getMigrationEntryMap();
        System.out.println(changelog);

        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(1)).checkIntegrity();

        List<MigrationChangelog> changelogs = migrationChangeRepository.findAll();
        assertThat(changelogs).hasSize(2);
        assertThat(changelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(1);
        assertThat(changelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(2);

        changelog.put(2, TestChangeEntry20211216_231800.class);

        registrySpyMock.execute(applicationReadyEventMock);

        verify(registrySpyMock, times(2)).checkIntegrity();

        List<MigrationChangelog> secondChangelogs = migrationChangeRepository.findAll();
        assertThat(secondChangelogs).hasSize(3);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getSystemVersion).distinct()).hasSize(1);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDeploymentId).distinct()).hasSize(2);
        assertThat(secondChangelogs.stream().map(MigrationChangelog::getDateString).distinct()).hasSize(3);
    }

    private MigrationEntry createEntryMock(String date, String author) {
        MigrationEntry mock = mock(MigrationEntry.class);
        when(mock.date()).thenReturn(date);
        when(mock.author()).thenReturn(author);
        return mock;
    }
}
