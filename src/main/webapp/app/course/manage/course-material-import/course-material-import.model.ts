/**
 * Options for importing course material.
 */
export interface CourseMaterialImportOptions {
    importExercises: boolean;
    importLectures: boolean;
    importExams: boolean;
    importCompetencies: boolean;
    importTutorialGroups: boolean;
    importFaqs: boolean;
}

/**
 * DTO for importing course material from another course.
 */
export interface CourseMaterialImportOptionsDTO extends CourseMaterialImportOptions {
    sourceCourseId: number;
}

/**
 * Result of a course material import operation.
 */
export interface CourseMaterialImportResultDTO {
    exercisesImported: number;
    lecturesImported: number;
    examsImported: number;
    competenciesImported: number;
    tutorialGroupsImported: number;
    faqsImported: number;
    errors: string[];
}

/**
 * Creates default import options with all options set to false.
 */
export function createDefaultImportOptions(): CourseMaterialImportOptions {
    return {
        importExercises: false,
        importLectures: false,
        importExams: false,
        importCompetencies: false,
        importTutorialGroups: false,
        importFaqs: false,
    };
}
