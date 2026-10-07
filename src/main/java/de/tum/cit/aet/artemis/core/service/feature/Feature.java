package de.tum.cit.aet.artemis.core.service.feature;

// Must be the same as FeatureToggle in feature-toggle.service.ts on the client side.
// The distributed feature map encodes these keys by position: adding, removing or reordering a constant needs a
// distributed-data schema migration, see DistributedDataSchema.
public enum Feature {
    ProgrammingExercises, PlagiarismChecks, Exports, LearningPaths, Science, StandardizedCompetencies, TutorSuggestions, AtlasAgent, Memiris, LectureContentProcessing, RateLimit,
    GlobalSearch, AutonomousTutor, Deimos, IrisProactiveStruggle, GlobalSearchReconcile, GlobalSearchReconcileOrphan
}
