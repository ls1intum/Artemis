import { CourseInfo, LectureInfo, LectureUnitInfo } from './lecture-search-result.model';

/**
 * Unified source result from the Iris answer pipeline.
 * Covers all entity types: lecture slides, exercises, FAQs, exams, channels.
 * The `sourceType` discriminator drives the icon selection in the UI.
 * The lecture search view still uses `LectureSearchResult` unchanged.
 */
export interface GlobalSearchSource {
    sourceType: string;
    entityId: number;
    course: CourseInfo;
    title: string;
    snippet?: string;
    /** Exercise sub-type (only when sourceType="exercise"): "programming" | "quiz" | "modeling" | "text" | "file-upload" */
    exerciseType?: string;
    // Lecture-specific (only present for lecture_unit_* source types)
    lecture?: LectureInfo;
    lectureUnit?: LectureUnitInfo;
}
