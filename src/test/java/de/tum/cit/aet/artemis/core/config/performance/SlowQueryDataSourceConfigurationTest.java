package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import de.tum.cit.aet.artemis.core.config.performance.SlowQueryDataSourceConfiguration.DataSourceProxyBeanPostProcessor;
import net.ttddyy.dsproxy.support.ProxyDataSource;

/**
 * Covers which beans the processor wraps with the query-timing proxy.
 */
class SlowQueryDataSourceConfigurationTest {

    private DataSourceProxyBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("listener", new SlowQueryListener(new SlowQueryCollector(new SlowQueryProperties())));
        processor = SlowQueryDataSourceConfiguration.dataSourceProxyBeanPostProcessor();
        processor.setApplicationContext(context);
    }

    @Test
    void theMainDataSourceIsWrappedWithTheListener() {
        DataSource dataSource = mock(DataSource.class);

        Object result = processor.postProcessAfterInitialization(dataSource, "dataSource");

        assertThat(result).isInstanceOf(ProxyDataSource.class);
        assertThat(((ProxyDataSource) result).getDataSourceName()).isEqualTo("artemis-slow-query-proxy");
    }

    @Test
    void otherBeansAndAlreadyWrappedDataSourcesAreLeftAlone() {
        DataSource other = mock(DataSource.class);
        ProxyDataSource wrapped = new ProxyDataSource(mock(DataSource.class));

        assertThat(processor.postProcessAfterInitialization(other, "liquibaseDataSource")).isSameAs(other);
        assertThat(processor.postProcessAfterInitialization(wrapped, "dataSource")).isSameAs(wrapped);
        assertThat(processor.postProcessAfterInitialization("not a data source", "dataSource")).isEqualTo("not a data source");
    }
}
