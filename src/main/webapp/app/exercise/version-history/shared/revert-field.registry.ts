/**
 * Registry of fields that can be safely reverted from the version history diff view.
 *
 * Each entry maps a field ID (as used in the MetadataField.id of the snapshot metadata
 * components) to its configuration for performing the revert.
 *
 * See revert-field-classification.md for the full reasoning behind each classification.
 */

export type RevertUpdateStrategy = 'full' | 'timeline' | 'problemStatement';
export type RevertValueType = 'string' | 'number' | 'boolean' | 'date';

export interface RevertableFieldConfig {
    /** Dot-separated path on the exercise entity, e.g. `'title'` or `'plagiarismDetectionConfig.similarityThreshold'`. */
    entityPath: string;
    /** Which backend endpoint to use for the update. */
    updateStrategy: RevertUpdateStrategy;
    /** The primitive type of the raw snapshot value, used for type conversion before sending to the backend. */
    valueType: RevertValueType;
}

/**
 * Map of field IDs to their revert configuration.
 * Only fields present in this map will show a revert button in the diff view.
 */
export const REVERTABLE_FIELDS: ReadonlyMap<string, RevertableFieldConfig> = new Map<string, RevertableFieldConfig>([
    // ──────────────────────────────────────────────────────────────────────
    // Only fields with NO cross-field validation constraints are included.
    //
    // Deferred to v2 (with reasons):
    //  - dates: ordering constraints, dueDate destructively removes individual due dates
    //  - plagiarism config: config-level validation may reject combinations
    //  - allowFeedbackRequests: cross-validates with assessmentType/dueDate/buildAndTest
    //  - allowOnlineIde: requires valid theiaImage
    //  - allowOnlineEditor/allowOfflineIde: "at least one mode enabled" cross-field check
    //  - includedInOverallScore: exam restriction (must be INCLUDED_COMPLETELY)
    //  - maxStaticCodeAnalysisPenalty: requires SCA enabled (immutable field)
    //  - channelName: duplicate channel name check can fail
    //  - submissionPolicy.*: null SubmissionPolicy object breaks nested path; per-field
    //    validation (limit >= 1, penalty > 0)
    //  - teamAssignment.*: only meaningful when exercise.mode === TEAM
    // ──────────────────────────────────────────────────────────────────────

    // --- General metadata (ExerciseVersionSharedSnapshotMetadataComponent) ---
    ['title', { entityPath: 'title', updateStrategy: 'full', valueType: 'string' }],
    ['difficulty', { entityPath: 'difficulty', updateStrategy: 'full', valueType: 'string' }],
    ['maxPoints', { entityPath: 'maxPoints', updateStrategy: 'full', valueType: 'number' }],
    ['bonusPoints', { entityPath: 'bonusPoints', updateStrategy: 'full', valueType: 'number' }],
    ['presentationScoreEnabled', { entityPath: 'presentationScoreEnabled', updateStrategy: 'full', valueType: 'boolean' }],
    ['secondCorrectionEnabled', { entityPath: 'secondCorrectionEnabled', updateStrategy: 'full', valueType: 'boolean' }],
    ['allowComplaintsForAutomaticAssessments', { entityPath: 'allowComplaintsForAutomaticAssessments', updateStrategy: 'full', valueType: 'boolean' }],

    // --- Problem statement (dedicated endpoint) ---
    ['problemStatement', { entityPath: 'problemStatement', updateStrategy: 'problemStatement', valueType: 'string' }],

    // --- Programming-specific fields (ProgrammingExerciseVersionProgrammingMetadataComponent) ---
    ['showTestNamesToStudents', { entityPath: 'showTestNamesToStudents', updateStrategy: 'full', valueType: 'boolean' }],
    ['releaseTestsWithExampleSolution', { entityPath: 'releaseTestsWithExampleSolution', updateStrategy: 'full', valueType: 'boolean' }],
]);

/** Returns whether a field ID is revertable. */
export function isRevertable(fieldId: string): boolean {
    return REVERTABLE_FIELDS.has(fieldId);
}

/** Returns the revert configuration for a field, or undefined if it is not revertable. */
export function getRevertConfig(fieldId: string): RevertableFieldConfig | undefined {
    return REVERTABLE_FIELDS.get(fieldId);
}
