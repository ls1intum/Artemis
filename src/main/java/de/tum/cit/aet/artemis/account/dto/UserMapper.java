package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Static factory for {@link UserSummaryDTO} instances.
 * <p>
 * Pure and side-effect free; intentionally not a Spring bean so it can be invoked from REST
 * response mapping paths without going through the application context.
 */
public final class UserMapper {

    private UserMapper() {
    }

    /**
     * Build a {@link UserSummaryDTO} from a {@link User} entity. Returns {@code null} if the
     * input is {@code null} so callers can map nullable references without a separate guard.
     *
     * @param user the user entity to project, may be {@code null}
     * @return the projected summary, or {@code null} when {@code user} is {@code null}
     */
    public static @Nullable UserSummaryDTO toSummary(@Nullable User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryDTO(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getName(), user.getImageUrl(), user.isBot());
    }
}
