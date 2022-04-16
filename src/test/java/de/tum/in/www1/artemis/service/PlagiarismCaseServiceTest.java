package de.tum.in.www1.artemis.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

public class PlagiarismCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @BeforeEach
    void init() {
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void testUpdatePlagiarismCaseVerdict() {
    }

    @Test
    public void testSavePostForPlagiarismCaseAndNotifyStudent() {
    }

    @Test
    public void testCreateOrAddPlagiarismCasesForComparison() {
    }
}
