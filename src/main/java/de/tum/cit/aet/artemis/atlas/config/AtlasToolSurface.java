package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Thin wrapper around a role-scoped {@link ToolCallbackProvider}.
 * <p>
 * Atlas wires one provider per agent role (see {@link AtlasAgentToolConfig}) and injects them by
 * qualifier. The wrapper exists so these surfaces are <em>not</em> exposed to the application context as
 * {@link ToolCallbackProvider} beans: Spring AI's {@code ToolCallingAutoConfiguration} collects every
 * {@code ToolCallbackProvider} bean into its global {@code ToolCallbackResolver}. That would (a) flatten
 * all role surfaces into one ambient tool set, defeating the per-role isolation, and (b) reintroduce a
 * circular dependency back to the chat stack
 * ({@code provider → AtlasAgentToolsService → AtlasAgentDelegationService → ChatClient →
 * ToolCallingManager → ToolCallbackResolver}). Keeping each provider behind a
 * non-{@code ToolCallbackProvider} bean type avoids both while preserving central, per-role,
 * qualifier-based wiring.
 *
 * @param provider the role-scoped tool callback provider
 */
public record AtlasToolSurface(ToolCallbackProvider provider) {
}
