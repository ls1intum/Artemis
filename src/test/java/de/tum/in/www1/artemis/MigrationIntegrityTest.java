package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.config.migration.MigrationRegistry;

public class MigrationIntegrityTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MigrationRegistry registry;

    @Test
    public void testIntegrity() {
        registry.instantiateEntryMap();

        assertThat(registry.checkIntegrity()).as("Migration Integrity Check")
                .withFailMessage("The Migration integrity check was not successful. Check the logs for errors in the registry.").isTrue();
    }

    @Test
    public void testValidMap() {
        TreeMap<Integer, MigrationEntry> map = new TreeMap<>() {

            {
                put(0, createEntryMock("20211214-231800", "Valid Author"));
                put(1, createEntryMock("20211215-231800", "Valid Author"));
                put(2, createEntryMock("20211216-231800", "Valid Author"));
            }
        };
        assertThat(registry.checkIntegrity(map)).isTrue();
        assertThat(registry.checkIntegrity(new TreeMap<>())).isTrue();
    }

    @Test
    public void testBrokenEntriesFail() {
        assertThat(registry.checkIntegrity(new TreeMap<>() {

            {
                put(0, createEntryMock("20211216-231800", null));
            }
        })).isFalse();
        assertThat(registry.checkIntegrity(new TreeMap<>() {

            {
                put(0, createEntryMock("20211216-231800", ""));
            }
        })).isFalse();
        assertThat(registry.checkIntegrity(new TreeMap<>() {

            {
                put(0, createEntryMock(null, "Valid Author"));
            }
        })).isFalse();
        assertThat(registry.checkIntegrity(new TreeMap<>() {

            {
                put(0, createEntryMock("", "Valid Author"));
            }
        })).isFalse();
    }

    @Test
    public void testDuplicateDateStringsFail() {
        assertThat(registry.checkIntegrity(new TreeMap<>() {

            {
                put(0, createEntryMock("20211216-231800", "Valid Author"));
                put(1, createEntryMock("20211216-231800", "Valid Author"));
            }
        })).isFalse();
    }

    @Test
    public void testWrongOrderDateStringsFail() {
        assertThat(registry.checkIntegrity(new TreeMap<>() {

            {
                put(0, createEntryMock("20211217-231800", "Valid Author"));
                put(1, createEntryMock("20211216-231800", "Valid Author"));
            }
        })).isFalse();
    }

    private MigrationEntry createEntryMock(String date, String author) {
        MigrationEntry mock = mock(MigrationEntry.class);
        when(mock.date()).thenReturn(date);
        when(mock.author()).thenReturn(author);
        return mock;
    }
}
