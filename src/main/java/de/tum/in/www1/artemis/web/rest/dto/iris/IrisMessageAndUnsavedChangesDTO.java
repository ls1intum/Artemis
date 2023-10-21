package de.tum.in.www1.artemis.web.rest.dto.iris;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public record IrisMessageAndUnsavedChangesDTO(IrisMessage message, UnsavedCodeEditorChangesDTO unsavedChanges) {
}
