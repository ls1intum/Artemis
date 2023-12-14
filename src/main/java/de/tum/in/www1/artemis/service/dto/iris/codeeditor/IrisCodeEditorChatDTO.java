package de.tum.in.www1.artemis.service.dto.iris.codeeditor;

import java.util.List;
import java.util.Map;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public record IrisCodeEditorChatDTO(String problemStatement, Map<String, String> solutionRepository, Map<String, String> templateRepository, Map<String, String> testRepository,
        List<IrisMessage> chatHistory) {
}
