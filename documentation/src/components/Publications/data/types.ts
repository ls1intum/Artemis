/**
 * Research themes used to group Artemis publications.
 *
 * A publication can belong to several themes; the values below are only a reading aid and do not
 * change the canonical bibliographic record.
 */
export enum ResearchTopic {
    InteractiveLearning = 'interactive-learning',
    ProgrammingEducation = 'programming-education',
    FeedbackAndAssessment = 'feedback-and-assessment',
    AdaptiveLearningAndAnalytics = 'adaptive-learning-and-analytics',
    AiInEducation = 'ai-in-education',
    OnlineExams = 'online-exams',
    PlatformResearch = 'platform-research',
}

export const researchTopicLabels: Record<ResearchTopic, string> = {
    [ResearchTopic.InteractiveLearning]: 'Interactive Learning',
    [ResearchTopic.ProgrammingEducation]: 'Programming Education',
    [ResearchTopic.FeedbackAndAssessment]: 'Feedback & Assessment',
    [ResearchTopic.AdaptiveLearningAndAnalytics]: 'Adaptive Learning & Learning Analytics',
    [ResearchTopic.AiInEducation]: 'AI in Education',
    [ResearchTopic.OnlineExams]: 'Online Exams & Assessment',
    [ResearchTopic.PlatformResearch]: 'Platform & Software Engineering Research',
};

/** Order in which the themes are rendered. */
export const researchTopicOrder: ResearchTopic[] = [
    ResearchTopic.InteractiveLearning,
    ResearchTopic.ProgrammingEducation,
    ResearchTopic.FeedbackAndAssessment,
    ResearchTopic.AdaptiveLearningAndAnalytics,
    ResearchTopic.AiInEducation,
    ResearchTopic.OnlineExams,
    ResearchTopic.PlatformResearch,
];

/**
 * A single bibliographic record.
 *
 * The fields mirror what a BibTeX entry or a DOI lookup provides, so this list can later be generated
 * from structured metadata instead of being maintained by hand.
 */
export interface Publication {
    /**
     * Stable identifier, used as the React key.
     *
     * Deliberately not rendered as a DOM `id`: the themed view shows a publication once per matching
     * theme, so a bare `id` would produce duplicates. A per-publication anchor needs a group-scoped
     * prefix.
     */
    id: string;
    title: string;
    authors: string;
    /** Venue including the abbreviated series name, as it appears in the official proceedings. */
    venue: string;
    /** Place and month of publication, plus page numbers where the venue provides them. */
    details?: string;
    year: number;
    /** Preferred persistent link. Use a DOI whenever one exists. */
    doi?: string;
    /** Fallback persistent link when no DOI exists (for example a handle or a CEUR-WS URL). */
    url?: string;
    /** Label shown for `url` entries, for example "hdl.handle.net/10125/79439". */
    urlLabel?: string;
    topics: ResearchTopic[];
}
