import { Validators } from '@angular/forms';
import { Params } from '@angular/router';
import dayjs from 'dayjs/esm';
import { Post } from './shared/entities/post.model';
import { User } from 'app/account/user/user.model';

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

export enum SortDirection {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
}

export enum PostSortCriterion {
    CREATION_DATE = 'CREATION_DATE',
}

export enum CommunicationCrudAction {
    CREATE = 'CREATE',
    UPDATE = 'UPDATE',
    DELETE = 'DELETE',
    NEW_MESSAGE = 'NEW_MESSAGE',
}

export interface PostContextFilter {
    courseId?: number;
    plagiarismCaseId?: number;
    searchText?: string;
    conversationIds?: number[];
    authorIds?: number[];
    filterToCourseWide?: boolean;
    filterToUnresolved?: boolean;
    filterToAnsweredOrReacted?: boolean;
    postSortCriterion?: PostSortCriterion;
    sortingOrder?: SortDirection;
    pagingEnabled?: boolean;
    page?: number;
    pageSize?: number;
    pinnedOnly?: boolean;
    filterToUnverifiedIris?: boolean;
}

/**
 * A reference within the content of a posting, e.g. a mention of a user or a link to a post, an exercise or an attachment.
 * The posting renders its content as one Markdown document and mounts a `PostingContentPart` where the Markdown placed the reference.
 * For example, the content "More information on this topic is in #5." has the `PostingContentPart`
 * { linkToReference: "/courses/{courseId}/discussion",
 *   queryParams: {searchText: #5},
 *   referenceStr: "#5",
 *   referenceType: ReferenceType.POST }
 */
export interface PostingContentPart {
    linkToReference?: RouteComponents; // link the reference navigates to
    attachmentToReference?: string; // attachment link the reference opens
    slideToReference?: string; // slide link the reference opens
    queryParams?: Params; // params that are required for navigating
    referenceStr?: string; // string that is within the anchor tag
    referenceType?: ReferenceType; // type of artifact to reference
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

export const CommunicationWebsocketChannelPrefix = '/topic/communication/';

/**
 * The ways a user takes over the scroll position of a message list. The browser fires no scroll event that tells their
 * scrolling apart from a position the application set itself.
 */
export const USER_SCROLL_EVENTS = ['wheel', 'touchstart', 'pointerdown', 'keydown'] as const;

/**
 * whitespace accepted only together with a character excluding newline character
 */
export const PostTitleValidationPattern = Validators.pattern(/^(.)*\S+(.)*$/);

/**
 * whitespace accepted only together with a character including newline character
 * */
export const PostContentValidationPattern = Validators.pattern(/^(\n|\r|.)*\S+(\n|\r|.)*$/);

export function getUnreadPostsByLastReadDate(user: User, posts: Post[], lastReadDate: dayjs.Dayjs): Post[] {
    return posts.filter((p) => p.author?.id !== user.id && p.creationDate?.isAfter(lastReadDate)).sort((a, b) => a.creationDate!.diff(b.creationDate));
}
