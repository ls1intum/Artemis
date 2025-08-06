package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

public record MemirisMemoryDTO(String id, String title, String content, List<String> learnings, List<String> connections, boolean slept_on, boolean deleted) {
}
