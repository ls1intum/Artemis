export enum PostingEditType {
    CREATE,
    UPDATE,
}

export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
}

export enum DisplayPriority {
    PINNED = 'PINNED',
    ARCHIVED = 'ARCHIVED',
    NONE = 'NONE',
}

export enum PageType {
    OVERVIEW = 'OVERVIEW',
    PAGE_SECTION = 'PAGE_SECTION',
}

export const VOTE_EMOJI_ID = 'heavy_plus_sign';

export enum SortDirection {
    ASC = 'ASC',
    DESC = 'DESC',
}

export enum PostSortCriterion {
    CREATION_DATE = 'CREATION_DATE',
    VOTES = 'VOTES',
    ANSWER_COUNT = 'ANSWER_COUNT',
}

export interface PostContextFilter {
    courseId?: number;
    courseWideContext?: CourseWideContext;
    exerciseId?: number;
    lectureId?: number;
}
