package de.tum.in.www1.artemis.web.rest.settings.ide;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.settings.ide.Ide;

/**
 *
 * @param programmingLanguage
 * @param ide
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IdeSettingsDTO(ProgrammingLanguage programmingLanguage, Ide ide) {
}
