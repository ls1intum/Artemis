package de.tum.cit.aet.artemis.tutorialgroup.service;

import java.time.LocalDate;
import java.util.List;

import de.tum.cit.aet.artemis.tutorialgroup.dto.PublicHolidayDTO;

/**
 * Supplies the public holidays an instructor can import into a course as tutorial group free periods.
 * <p>
 * The source of that data is deliberately not decided here. A bundled dataset, a call to an external holiday service and
 * a per-institution configuration all fit behind this interface, and which one Artemis should ship is an open question -
 * so the only implementation right now is {@link UnconfiguredPublicHolidayService}, which offers nothing and says so.
 * Replacing it means adding a bean that takes precedence; no caller changes.
 */
public interface PublicHolidayProvider {

    /**
     * Whether this provider can supply holidays at all.
     * <p>
     * Separate from an empty result: a configured provider legitimately returns nothing for a span that contains no
     * holidays, and the client needs to tell that apart from having no source of holidays in the first place.
     *
     * @return true when {@link #findHolidaysBetween} can return meaningful data
     */
    boolean isConfigured();

    /**
     * Finds the public holidays that fall within the given span.
     *
     * @param from the inclusive first day to consider
     * @param to   the inclusive last day to consider
     * @return the holidays in the span, ascending by date; empty when there are none or no source is configured
     */
    List<PublicHolidayDTO> findHolidaysBetween(LocalDate from, LocalDate to);
}
