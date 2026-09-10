package de.tum.cit.aet.artemis.tutorialgroup.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.dto.PublicHolidayDTO;

/**
 * The fallback {@link PublicHolidayProvider}, in place until Artemis decides where public holidays should come from.
 * <p>
 * It reports itself unconfigured and returns nothing, which the client renders as an explanation rather than as an empty
 * list of holidays. {@link ConditionalOnMissingBean} means a real provider added later simply replaces it.
 */
@Conditional(TutorialGroupEnabled.class)
@Lazy
@Service
@ConditionalOnMissingBean(ignored = UnconfiguredPublicHolidayService.class, value = PublicHolidayProvider.class)
public class UnconfiguredPublicHolidayService implements PublicHolidayProvider {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public List<PublicHolidayDTO> findHolidaysBetween(LocalDate from, LocalDate to) {
        return List.of();
    }
}
