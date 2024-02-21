package de.tum.in.www1.artemis.service.connectors.iris.dto;

public record PyrisWebhookDTO(PyrisWebhookType type, Object payload, PyrisWebhookSettingsDTO settings) {
}
