package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The preset one user has selected for one course, carrying the user so a whole cohort can be read in one query.
 * <p>
 * Sending a course notification filters every recipient against their settings, once per delivery channel. Asking per
 * user meant a query per recipient per channel - 9000 of them for a 3000 student announcement across three channels -
 * which is why that read used to be cached. Reading the cohort at once removes the need for either.
 *
 * @param userId        the user the preset belongs to
 * @param settingPreset the identifier of the preset the user selected, where 0 means a custom preset
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationSettingPresetEntryDTO(long userId, short settingPreset) {
}
