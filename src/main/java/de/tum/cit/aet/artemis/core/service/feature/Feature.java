package de.tum.cit.aet.artemis.core.service.feature;

// Must be the same as FeatureToggle in feature-toggle.service.ts on the client side
// Append new constants only: Redis map keys use Kryo's ordinal-based enum encoding.
public enum Feature {
    ProgrammingExercises, PlagiarismChecks, Exports, LearningPaths, Science, StandardizedCompetencies, TutorSuggestions, AtlasML, AtlasAgent, Memiris, LectureContentProcessing,
    RateLimit, GlobalSearch, AutonomousTutor, Deimos, IrisProactiveStruggle, GlobalSearchReconcile, GlobalSearchReconcileOrphan, PresentationAssessments
}
