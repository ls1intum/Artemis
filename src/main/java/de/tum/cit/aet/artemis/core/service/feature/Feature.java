package de.tum.cit.aet.artemis.core.service.feature;

// Must be the same as FeatureToggle in feature-toggle.service.ts on the client side.
// AtlasML is unused and kept only because the distributed feature map encodes its keys by position; it is removed together with a distributed-data schema migration.
public enum Feature {
    ProgrammingExercises, PlagiarismChecks, Exports, LearningPaths, Science, StandardizedCompetencies, TutorSuggestions, AtlasML, AtlasAgent, Memiris, LectureContentProcessing,
    RateLimit, GlobalSearch, AutonomousTutor, Deimos, IrisProactiveStruggle, GlobalSearchReconcile, GlobalSearchReconcileOrphan
}
