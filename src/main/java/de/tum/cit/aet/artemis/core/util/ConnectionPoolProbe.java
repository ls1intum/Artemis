package de.tum.cit.aet.artemis.core.util;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import javax.sql.DataSource;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Reports the state of the database connection pool, for diagnosing whether a slow request was waiting for a connection
 * rather than doing work.
 * <p>
 * A request that is slow while {@code pending} is zero was not starved of connections, and the time has to be explained
 * by what the request itself did. A request that is slow while {@code pending} is high was queued behind other users of
 * the pool, and the cause is whatever is holding connections rather than the request.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class ConnectionPoolProbe {

    private final DataSource dataSource;

    public ConnectionPoolProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return a short description of the pool, or "unavailable" if the pool does not expose its state
     */
    public String snapshot() {
        if (dataSource instanceof HikariDataSource hikariDataSource && hikariDataSource.getHikariPoolMXBean() != null) {
            var pool = hikariDataSource.getHikariPoolMXBean();
            return "active=" + pool.getActiveConnections() + " idle=" + pool.getIdleConnections() + " pending=" + pool.getThreadsAwaitingConnection() + " total="
                    + pool.getTotalConnections();
        }
        return "unavailable";
    }
}
