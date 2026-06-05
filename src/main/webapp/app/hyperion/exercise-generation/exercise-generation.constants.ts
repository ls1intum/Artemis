/**
 * Router navigation-state flag set when an instructor creates an exercise via "Generate entire exercise": the exercise update flow passes it, and the detail page reads it to
 * auto-start the generation run. Kept in a standalone module so the producer (update) and consumer (detail) share one literal without importing each other's heavy graphs.
 */
export const AUTO_START_EXERCISE_GENERATION_STATE = 'autoStartExerciseGeneration';
