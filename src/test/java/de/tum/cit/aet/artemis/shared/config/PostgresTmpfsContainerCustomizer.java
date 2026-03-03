package de.tum.cit.aet.artemis.shared.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import io.zonky.test.db.provider.postgres.PostgreSQLContainerCustomizer;

/**
 * Fixes tmpfs support for PostgreSQL 18+ Docker images used by Zonky Testcontainers.
 * <p>
 * <b>Problem:</b> Starting with PostgreSQL 18, the official Docker image moved PGDATA from
 * {@code /var/lib/postgresql/data} to {@code /var/lib/postgresql/<major>/docker}
 * (see <a href="https://github.com/docker-library/postgres/pull/1259">docker-library/postgres#1259</a>).
 * Zonky's built-in tmpfs mount targets the old path ({@code /var/lib/postgresql/data}),
 * so the container fails to start with exit code 1 because PGDATA is not on the tmpfs mount.
 * <p>
 * <b>Solution:</b> Disable Zonky's built-in tmpfs in {@code application.yml}
 * ({@code zonky.test.database.postgres.docker.tmpfs.enabled: false}) and mount tmpfs at the
 * parent directory {@code /var/lib/postgresql} instead. This covers all PostgreSQL versions
 * regardless of the exact PGDATA subdirectory layout.
 * <p>
 * <b>Why tmpfs?</b> Running PostgreSQL on tmpfs (RAM-backed filesystem) significantly speeds up
 * test execution by avoiding disk I/O for WAL writes, checkpoints, and data files. Combined with
 * the PostgreSQL performance tuning in {@code application.yml} (fsync=off, synchronous_commit=off),
 * this makes Testcontainer-based PostgreSQL tests run nearly as fast as H2.
 *
 * @see TestDataSourcePoolConfig
 */
@Configuration
@Lazy
public class PostgresTmpfsContainerCustomizer {

    @Bean
    PostgreSQLContainerCustomizer postgresTmpfs() {
        return container -> container.withTmpFs(Map.of("/var/lib/postgresql", "rw,noexec,nosuid"));
    }
}
