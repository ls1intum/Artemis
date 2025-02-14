package de.tum.cit.aet.artemis.core.config.builder.properties;

import static de.tum.cit.aet.artemis.core.config.conditions.ConditionHelper.isBuildAgentEnabled;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.builder.CustomProperty;

public class AutoConfigurationExclusionProperty implements CustomProperty {

    private static final List<Class<?>> autoConfigurationExclusions = List.of(org.springframework.boot.actuate.autoconfigure.metrics.data.RepositoryMetricsAutoConfiguration.class,
            org.springframework.boot.actuate.autoconfigure.metrics.jdbc.DataSourcePoolMetricsAutoConfiguration.class,
            org.springframework.boot.actuate.autoconfigure.metrics.startup.StartupTimeMetricsListenerAutoConfiguration.class,
            org.springframework.boot.actuate.autoconfigure.metrics.task.TaskExecutorMetricsAutoConfiguration.class,
            org.springframework.boot.actuate.autoconfigure.metrics.web.tomcat.TomcatMetricsAutoConfiguration.class);

    private static final List<Class<?>> autoConfigurationExclusionsBuildagentOnly = List.of(org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class);

    @Override
    public String getPropertyName() {
        return "spring.autoconfigure.exclude";
    }

    @Override
    public Object getPropertyValue(ConfigurableEnvironment environment) {
        List<String> exclusions = new ArrayList<>();
        autoConfigurationExclusions.forEach(exclusion -> exclusions.add(toFQDN(exclusion)));

        if (isBuildAgentEnabled(environment)) {
            autoConfigurationExclusionsBuildagentOnly.forEach(exclusion -> exclusions.add(toFQDN(exclusion)));
        }

        return exclusions;
    }

    private String toFQDN(Class<?> clazz) {
        return clazz.getName();
    }
}
