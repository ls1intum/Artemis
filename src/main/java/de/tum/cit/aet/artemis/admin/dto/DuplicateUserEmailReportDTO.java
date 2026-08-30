package de.tum.cit.aet.artemis.admin.dto;

import java.util.List;

/**
 * Privacy-minimized data for the weekly duplicate-user-email administrator report.
 * Email addresses and user profile data are deliberately excluded.
 *
 * @param affectedAccountCount total number of accounts with a duplicated email
 * @param accountIds           capped list of affected numeric account identifiers
 */
public record DuplicateUserEmailReportDTO(int affectedAccountCount, List<Long> accountIds) {

    public int omittedAccountCount() {
        return affectedAccountCount - accountIds.size();
    }
}
