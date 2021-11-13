import { Params } from '@angular/router';

export enum PostingEditType {
    CREATE,
    UPDATE,
}

export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
    ANNOUNCEMENT = 'ANNOUNCEMENT',
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

export enum MetisPostAction {
    CREATE_POST = 'CREATE_POST',
    UPDATE_POST = 'UPDATE_POST',
    DELETE_POST = 'DELETE_POST',
}

export interface PostContextFilter {
    courseId?: number;
    courseWideContext?: CourseWideContext;
    exerciseId?: number;
    lectureId?: number;
}

/**
 * The content of a Post is composed of `PostingContentPart` that allows rendering the parts individually and creating references to other posts.
 * For example, the content "You can find more information on this topic in #5 and #6 as well." will be split up in two separate `PostingContentPart`s:
 * { contentBeforeReference: "You can find more information on this topic in ",
 *   linkToReference: "/courses/{courseId}/discussion",
 *   queryParams: {searchText: #5},
 *   referenceStr: "#5",
 *   contentAfterReference: " and " }
 * The second PostingContentPart is:
 * { contentBeforeReference: undefined // only exists for the first PostingContentPart,
 *   linkToReference: "/courses/{courseId}/discussion",
 *   queryParams: {searchText: #6},
 *   referenceStr: "#6",
 *   contentAfterReference: " as well. " }
 */
export interface PostingContentPart {
    contentBeforeReference?: string; // string before occurrence of reference pattern -> only for the first PostContentPart in the content of a posting
    linkToReference?: RouteComponents; // link the reference navigates to
    queryParams?: Params; // params that are required for navigating
    referenceStr?: string; // string that is within the anchor tag
    contentAfterReference?: string; // string after occurrence of reference pattern
}

/**
 * For each match that is found during regex search on a posting content string, the start index of the match as well as the inclusive end index of the match is stored.
 * For example, if we search for the reference pattern in the string "I reference #54.", the resulting PatternMatch object would be:
 * { startIndex: 12, endIndex: 15}
 */
export interface PatternMatch {
    startIndex: number;
    endIndex: number;
}

/**
 * The context information of a post contains - for exercise and lecture context - an array of link components to be used by the Router to navigate to the context,
 * and the display name, i.e. the string that is linked, e.g the lecture title
 */
export interface ContextInformation {
    routerLinkComponents?: RouteComponents;
    displayName: string;
}

/**
 * Helper type reflecting components used by the angular router,
 * each component is either a string or a number
 */
export type RouteComponents = (string | number)[];

export const MetisWebsocketChannelPrefix = '/topic/metis/';
