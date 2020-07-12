package de.tum.in.www1.artemis.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@Order(2)
@ConditionalOnProperty(prefix = "management", name = "metrics.export.prometheus.enabled")
public class PrometheusAuthConfiguration extends WebSecurityConfigurerAdapter {

    @Value("${spring.prometheus.monitoringIp:#{null}}")
    private Optional<String> monitoringIpAddress;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Only enable the endpoint if an ip-address is specified
        if (monitoringIpAddress.isPresent()) {
            if (!monitoringIpAddress.get().isEmpty()) {
                http.authorizeRequests().antMatchers("/management/prometheus/**").hasIpAddress(monitoringIpAddress.get()).anyRequest().authenticated().and().sessionManagement()
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().csrf().disable();
            }
            else {
                http.authorizeRequests().antMatchers("/management/prometheus/**").denyAll();
            }
        }
    }
}
