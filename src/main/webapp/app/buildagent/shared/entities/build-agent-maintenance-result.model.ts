/**
 * Outcome of a build-agent maintenance action. Mirrors the Java DTO
 * {@code BuildAgentMaintenanceResult} pushed by the agent that ran the action onto the per-agent
 * WebSocket topic {@code /topic/admin/build-agent/<short-name>/maintenance}.
 */
export interface BuildAgentMaintenanceResult {
    agentShortName: string;
    timestamp: string; // ISO-8601
    actionType: 'RUN_CACHE_CLEANUP' | 'WIPE_MAVEN_CACHE' | 'WIPE_GRADLE_CACHE' | 'CLEAR_DOCKER_IMAGES';
    outcome: 'SUCCESS' | 'PARTIAL_FAILURE' | 'FAILED' | 'SKIPPED';
    bytesFreed: number;
    itemsAffected: number;
    errorCount: number;
    durationMs: number;
    skipReason?: string;
    message?: string;
}
