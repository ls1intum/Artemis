import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { CourseWideContext } from 'app/entities/metis/post.model';

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
    CREATION_DATE = 'CREATION DATE',
    VOTES = 'VOTES',
    ANSWER_COUNT = 'ANSWER COUNT',
}

export interface PostFilter {
    course?: Course;
    exercise?: Exercise;
    lecture?: Lecture;
    courseWideContext?: CourseWideContext;
    sortDirection?: SortDirection;
    sortBy?: PostSortCriterion;
    searchText?: string;
}
