import { Validators } from '@angular/forms';
import { Params } from '@angular/router';

export enum PostingEditType {
    CREATE,
    UPDATE,
}

export enum DisplayPriority {
    PINNED = 'PINNED',
    ARCHIVED = 'ARCHIVED',
    NONE = 'NONE',
}

export enum PageType {
    OVERVIEW = 'OVERVIEW',
    PAGE_SECTION = 'PAGE_SECTION',
    PLAGIARISM_CASE_INSTRUCTOR = 'PLAGIARISM_CASE_INSTRUCTOR',
    PLAGIARISM_CASE_STUDENT = 'PLAGIARISM_CASE_STUDENT',
}

export const VOTE_EMOJI_ID = 'heavy_plus_sign';

export enum SortDirection {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
}

export enum PostSortCriterion {
    CREATION_DATE = 'CREATION_DATE',
    VOTES = 'VOTES',
}

export enum MetisPostAction {
    CREATE = 'CREATE',
    UPDATE = 'UPDATE',
    DELETE = 'DELETE',

    NEW_MESSAGE = 'NEW_MESSAGE',
}

export interface PostContextFilter {
    courseId?: number;
    courseWideChannelIds?: number[];
    plagiarismCaseId?: number;
    searchText?: string;
    conversationId?: number;
    filterToUnresolved?: boolean;
    filterToOwn?: boolean;
    filterToAnsweredOrReacted?: boolean;
    postSortCriterion?: PostSortCriterion;
    sortingOrder?: SortDirection;
    pagingEnabled?: boolean;
    page?: number;
    pageSize?: number;
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
    attachmentToReference?: string; // attachment link the reference opens
    slideToReference?: string; // slide link the reference opens
    queryParams?: Params; // params that are required for navigating
    referenceStr?: string; // string that is within the anchor tag
    referenceType?: ReferenceType; // type of artifact to reference
    contentAfterReference?: string; // string after occurrence of reference pattern
    imageToReference?: string; // image link the reference opens
}

/**
 * For each match that is found during regex search on a posting content string, the start index of the match as well as the inclusive end index of the match is stored.
 * For example, if we search for the reference pattern in the string "I reference #54.", the resulting PatternMatch object would be:
 * { startIndex: 12, endIndex: 15}
 */
export interface PatternMatch {
    startIndex: number;
    endIndex: number;
    referenceType: ReferenceType;
}

export enum ReferenceType {
    POST = 'POST',
    LECTURE = 'LECTURE',
    ATTACHMENT = 'ATTACHMENT',
    ATTACHMENT_UNITS = 'ATTACHMENT_UNITS',
    SLIDE = 'SLIDE',
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload',
    USER = 'USER',
    CHANNEL = 'CHANNEL',
    FAQ = 'FAQ',
    IMAGE = 'IMAGE',

}

export enum UserRole {
    INSTRUCTOR = 'INSTRUCTOR',
    TUTOR = 'TUTOR',
    USER = 'USER',
}

/**
 * The context information of a post contains - for exercise and lecture context - an array of link components to be used by the Router to navigate to the context,
 * and the display name, i.e. the string that is linked, e.g. the lecture title
 */
export interface ContextInformation {
    routerLinkComponents?: RouteComponents;
    queryParams?: Params;
    displayName: string;
}

/**
 * Helper type reflecting components used by the angular router,
 * each component is either a string or a number
 */
export type RouteComponents = (string | number)[];

export const MetisWebsocketChannelPrefix = '/topic/metis/';

/**
 * whitespace accepted only together with a character excluding newline character
 */
export const PostTitleValidationPattern = Validators.pattern(/^(.)*\S+(.)*$/);

/**
 * whitespace accepted only together with a character including newline character
 * */
export const PostContentValidationPattern = Validators.pattern(/^(\n|\r|.)*\S+(\n|\r|.)*$/);
