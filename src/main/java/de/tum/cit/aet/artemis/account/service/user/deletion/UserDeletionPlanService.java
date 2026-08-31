package de.tum.cit.aet.artemis.account.service.user.deletion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.BulkUserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactCategoryDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;

/**
 * Produces the exact impact used by preview and execution. Dates are deliberately not used as a substitute for checking
 * references: automatic and provisional deletion are eligible only after all business-domain owners removed their rows.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserDeletionPlanService {

    private static final String POLICY_VERSION = "2";

    private final JdbcTemplate jdbcTemplate;

    private volatile Set<String> availableTableNames;

    public UserDeletionPlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserDeletionImpactDTO createImpact(User user, UserDeletionMode mode) {
        Map<UserDeletionReferencePolicy, Long> counts = countReferences(List.of(user.getId())).get(user.getId());
        return createImpact(user, mode, counts);
    }

    private UserDeletionImpactDTO createImpact(User user, UserDeletionMode mode, Map<UserDeletionReferencePolicy, Long> counts) {
        Map<CategoryAction, Long> groupedCounts = new LinkedHashMap<>();
        boolean automaticEligible = true;
        long totalAffectedObjects = 0;

        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            long count = counts.getOrDefault(policy, 0L);
            if (count == 0) {
                continue;
            }
            totalAffectedObjects += count;
            groupedCounts.merge(new CategoryAction(policy.category(), policy.action()), count, Long::sum);
            if (policy.automaticBlocker()) {
                automaticEligible = false;
            }
        }

        List<UserDeletionImpactCategoryDTO> categories = groupedCounts.entrySet().stream()
                .map(entry -> new UserDeletionImpactCategoryDTO(entry.getKey().category(), entry.getKey().action(), entry.getValue()))
                .sorted(Comparator.comparing((UserDeletionImpactCategoryDTO item) -> item.category().name()).thenComparing(item -> item.action().name())).toList();
        String fingerprint = fingerprint(user.getId(), counts);
        boolean retentionOverrideRequired = mode == UserDeletionMode.ADMIN_FORCED && !automaticEligible;
        return new UserDeletionImpactDTO(user.getId(), user.getLogin(), automaticEligible, user.isDeleted(), retentionOverrideRequired, totalAffectedObjects, fingerprint,
                categories);
    }

    public BulkUserDeletionImpactDTO createBulkImpact(List<User> users, UserDeletionMode mode) {
        Map<Long, Map<UserDeletionReferencePolicy, Long>> countsByUserId = countReferences(users.stream().map(User::getId).toList());
        List<UserDeletionImpactDTO> impacts = users.stream().map(user -> createImpact(user, mode, countsByUserId.get(user.getId()))).toList();
        Map<CategoryAction, Long> aggregate = new LinkedHashMap<>();
        impacts.stream().flatMap(impact -> impact.categories().stream())
                .forEach(item -> aggregate.merge(new CategoryAction(item.category(), item.action()), item.count(), Long::sum));
        List<UserDeletionImpactCategoryDTO> categories = aggregate.entrySet().stream()
                .map(entry -> new UserDeletionImpactCategoryDTO(entry.getKey().category(), entry.getKey().action(), entry.getValue()))
                .sorted(Comparator.comparing((UserDeletionImpactCategoryDTO item) -> item.category().name()).thenComparing(item -> item.action().name())).toList();
        long total = impacts.stream().mapToLong(UserDeletionImpactDTO::totalAffectedObjects).sum();
        return new BulkUserDeletionImpactDTO(impacts, total, categories);
    }

    public List<Long> findLegacyDeletedUserIds() {
        return jdbcTemplate.queryForList("SELECT id FROM jhi_user WHERE is_deleted = TRUE", Long.class);
    }

    /**
     * Returns the policies applicable to the active deployment. The registry deliberately includes optional modules,
     * while their tables are absent when the corresponding profile is disabled.
     *
     * @return policies whose referenced table exists in the active database schema
     */
    public List<UserDeletionReferencePolicy> availablePolicies() {
        Set<String> tables = availableTableNames();
        return List.of(UserDeletionReferencePolicy.values()).stream().filter(policy -> tables.contains(policy.tableName())).toList();
    }

    public boolean isTableAvailable(String tableName) {
        return availableTableNames().contains(tableName);
    }

    private Map<Long, Map<UserDeletionReferencePolicy, Long>> countReferences(List<Long> userIds) {
        Map<Long, Map<UserDeletionReferencePolicy, Long>> result = new LinkedHashMap<>();
        userIds.forEach(userId -> result.put(userId, new EnumMap<>(UserDeletionReferencePolicy.class)));
        if (userIds.isEmpty()) {
            return result;
        }

        if (userIds.size() == 1) {
            long userId = userIds.getFirst();
            List<UserDeletionReferencePolicy> policies = availablePolicies();
            List<String> statements = new ArrayList<>();
            Object[] parameters = new Object[policies.size()];
            int index = 0;
            for (UserDeletionReferencePolicy policy : policies) {
                statements.add("SELECT '" + policy.name() + "' AS policy_name, COUNT(*) AS reference_count FROM " + policy.tableName() + " WHERE " + policy.columnName() + " = ?");
                parameters[index++] = userId;
            }
            jdbcTemplate.query(String.join(" UNION ALL ", statements), resultSet -> {
                UserDeletionReferencePolicy policy = UserDeletionReferencePolicy.valueOf(resultSet.getString("policy_name"));
                result.get(userId).put(policy, resultSet.getLong("reference_count"));
            }, parameters);
            return result;
        }

        String placeholders = String.join(", ", userIds.stream().map(ignored -> "?").toList());
        Object[] parameters = userIds.toArray();
        for (UserDeletionReferencePolicy policy : availablePolicies()) {
            String sql = "SELECT " + policy.columnName() + ", COUNT(*) FROM " + policy.tableName() + " WHERE " + policy.columnName() + " IN (" + placeholders + ") GROUP BY "
                    + policy.columnName();
            jdbcTemplate.query(sql, resultSet -> {
                result.get(resultSet.getLong(1)).put(policy, resultSet.getLong(2));
            }, parameters);
        }
        return result;
    }

    private Set<String> availableTableNames() {
        Set<String> cached = availableTableNames;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (availableTableNames == null) {
                availableTableNames = jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
                    Set<String> tables = new HashSet<>();
                    try (ResultSet resultSet = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] { "TABLE" })) {
                        while (resultSet.next()) {
                            tables.add(resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                        }
                    }
                    return Set.copyOf(tables);
                });
            }
            return availableTableNames;
        }
    }

    private String fingerprint(long userId, Map<UserDeletionReferencePolicy, Long> counts) {
        List<String> parts = new ArrayList<>();
        parts.add(POLICY_VERSION);
        parts.add(Long.toString(userId));
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            parts.add(policy.name() + "=" + counts.getOrDefault(policy, 0L));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private record CategoryAction(UserDeletionDataCategory category, UserDeletionAction action) {
    }
}
