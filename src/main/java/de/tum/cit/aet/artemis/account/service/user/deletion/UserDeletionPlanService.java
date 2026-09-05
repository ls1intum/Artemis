package de.tum.cit.aet.artemis.account.service.user.deletion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.BulkUserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactCategoryDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

/**
 * Produces the exact impact used by preview and execution. Dates are deliberately not used as a substitute for checking
 * references: automatic and provisional deletion are eligible only after all business-domain owners removed their rows.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserDeletionPlanService {

    private static final String POLICY_VERSION = "3";

    private final UserRepository userRepository;

    private final UserReferenceCleanupService userReferenceCleanupService;

    public UserDeletionPlanService(UserRepository userRepository, UserReferenceCleanupService userReferenceCleanupService) {
        this.userRepository = userRepository;
        this.userReferenceCleanupService = userReferenceCleanupService;
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
        String fingerprint = fingerprint(user, mode, counts);
        boolean retentionOverrideRequired = mode == UserDeletionMode.ADMIN_FORCED && !automaticEligible;
        return new UserDeletionImpactDTO(user.getId(), user.getLogin(), automaticEligible, user.isDeleted(), retentionOverrideRequired, totalAffectedObjects, fingerprint,
                categories);
    }

    /**
     * The combined impact of deleting several accounts, counted in one pass over the reference policies rather than
     * once per account.
     *
     * @param users the accounts to preview
     * @param mode  what the deletion is allowed to remove, which decides what counts as a blocker
     * @return the per-account impacts together with the totals across all of them
     */
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
        return userRepository.findLegacyDeletedUserIds();
    }

    /**
     * Counts every reference of every account in one pass: one query per reference, whatever the number of accounts,
     * rather than one query per account and reference.
     */
    private Map<Long, Map<UserDeletionReferencePolicy, Long>> countReferences(List<Long> userIds) {
        Map<Long, Map<UserDeletionReferencePolicy, Long>> result = new LinkedHashMap<>();
        userIds.forEach(userId -> result.put(userId, new EnumMap<>(UserDeletionReferencePolicy.class)));
        if (userIds.isEmpty()) {
            return result;
        }
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            userReferenceCleanupService.count(policy, userIds).forEach((userId, count) -> result.get(userId).put(policy, count));
        }
        return result;
    }

    private String fingerprint(User user, UserDeletionMode mode, Map<UserDeletionReferencePolicy, Long> counts) {
        List<String> parts = new ArrayList<>();
        parts.add(POLICY_VERSION);
        parts.add(Long.toString(user.getId()));
        // The confirmation protects the target identity and authorization boundary as well as reference counts. Without
        // these values, an account renamed or promoted to administrator between preview and execution could still match
        // an earlier confirmation even though the administrator is no longer confirming the same deletion plan.
        parts.add("login=" + user.getLogin());
        parts.add("activated=" + user.getActivated());
        parts.add("deleted=" + user.isDeleted());
        // Automatic preview queries intentionally do not fetch authorities. Only an administrator-confirmed plan needs
        // role changes in its fingerprint; automatic execution checks protected roles after locking and fetching them.
        if (mode == UserDeletionMode.ADMIN_FORCED) {
            parts.add("authorities=" + user.getAuthorities().stream().map(authority -> authority.getName()).sorted().toList());
        }
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
