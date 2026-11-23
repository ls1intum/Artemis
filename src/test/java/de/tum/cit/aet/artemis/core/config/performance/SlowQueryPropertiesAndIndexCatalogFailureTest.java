package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

/**
 * Covers the configuration defaults and what the index catalog does when the database cannot be read.
 */
class SlowQueryPropertiesAndIndexCatalogFailureTest {

    @Test
    void thresholdAndLimitCanBeConfiguredAndDefaultToHundredMillisecondsAndTenThousandEntries() {
        SlowQueryProperties properties = new SlowQueryProperties();
        assertThat(properties.getSlowQueryThresholdMs()).isEqualTo(100);
        assertThat(properties.getMaxRecordedQueries()).isEqualTo(10_000);

        properties.setSlowQueryThresholdMs(5);
        properties.setMaxRecordedQueries(7);

        assertThat(properties.getSlowQueryThresholdMs()).isEqualTo(5);
        assertThat(properties.getMaxRecordedQueries()).isEqualTo(7);
    }

    @Test
    void anUnreadableDatabaseGivesNoIndexesAndIsOnlyAskedOnce() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));
        IndexCatalog catalog = new IndexCatalog(dataSource);

        assertThat(catalog.indexes()).isEmpty();
        assertThat(catalog.indexes()).isEmpty();

        verify(dataSource, times(1)).getConnection();
    }
}
