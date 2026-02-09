package de.tum.cit.aet.artemis.shared.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import io.zonky.test.db.provider.postgres.PostgreSQLContainerCustomizer;

/**
 * Fixes tmpfs support for PostgreSQL 18+ Docker images.
 * <p>
 * Starting with PostgreSQL 18, the official Docker image moved PGDATA from
 * {@code /var/lib/postgresql/data} to {@code /var/lib/postgresql/<major>/docker}
 * (see <a href="https://github.com/docker-library/postgres/pull/1259">docker-library/postgres#1259</a>).
 * Zonky's built-in tmpfs mount targets the old path, so we disable it in application.yml
 * and mount tmpfs at the parent directory instead, which covers all PostgreSQL versions.
 */
@Configuration
@Lazy
public class PostgresTmpfsContainerCustomizer {

    @Bean
    PostgreSQLContainerCustomizer postgresTmpfs() {
        return container -> container.withTmpFs(Map.of("/var/lib/postgresql", "rw,noexec,nosuid"));
    }
}
