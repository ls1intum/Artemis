package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The public holidays offered for import, together with whether a source of them exists at all.
 *
 * @param configured whether a {@link de.tum.cit.aet.artemis.tutorialgroup.service.PublicHolidayProvider} is in place;
 *                       when false the client explains that importing is not available rather than showing an empty list
 * @param holidays   the holidays in the requested span, ascending by date
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PublicHolidaySuggestionsDTO(boolean configured, List<PublicHolidayDTO> holidays) {
}
