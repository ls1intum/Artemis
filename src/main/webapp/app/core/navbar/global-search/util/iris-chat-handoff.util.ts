import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { convertCitationMarkers } from 'app/core/navbar/global-search/util/iris-citation-markers.util';
import { CitedSources, citedEntitySource, citedLectureSource } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { ChatServiceMode, EXERCISE_TYPE_TO_CHAT_MODE } from 'app/iris/shared/entities/iris-session-context.model';

/** A place a global search answer can be continued in: a course's Iris chat, optionally on one of its lectures or exercises. */
export interface IrisHandoffTarget {
    courseId: number;
    courseName: string;
    /** The lecture or exercise the chat starts on; undefined for the course itself. */
    context?: { mode: ChatServiceMode; entityId: number };
    /** The lecture or exercise, or the course when there is none. */
    name: string;
    /** How many of the answer's sources belong here. */
    citationCount: number;
}

/** One course the answer cites, with its places in the order the menu lists them. */
export interface IrisHandoffCourse {
    courseId: number;
    courseName: string;
    /** The lectures and exercises the answer cites in this course, most cited first. */
    places: IrisHandoffTarget[];
    /** The course chat itself. */
    wholeCourse: IrisHandoffTarget;
}

/** Where a global search answer can be continued. */
export interface IrisHandoffOptions {
    /** Where the button goes: the most cited lecture or exercise of the most cited course, or that course itself. */
    defaultTarget: IrisHandoffTarget;
    /** Every course the answer cites, most cited first. */
    courses: IrisHandoffCourse[];
    /** Whether there is anything to choose: several courses, or several lectures and exercises. */
    hasChoice: boolean;
}

/**
 * Collects the places the answer's sources belong to. A lecture source belongs to its lecture, an exercise with an Iris chat
 * to its exercise, a lecture entry to its lecture, and every other source (FAQs, exams, channels, course entries) only to its
 * course. Ties keep the order the sources were cited in.
 *
 * @returns the options, or undefined when no source names a course
 */
export function handoffOptions(cited: CitedSources): IrisHandoffOptions | undefined {
    const courses = new Map<number, IrisHandoffCourse>();
    const count = (course: { id: number; name: string }, place?: { context: IrisHandoffTarget['context']; name: string }) => {
        let entry = courses.get(course.id);
        if (!entry) {
            entry = {
                courseId: course.id,
                courseName: course.name,
                places: [],
                wholeCourse: { courseId: course.id, courseName: course.name, name: course.name, citationCount: 0 },
            };
            courses.set(course.id, entry);
        }
        entry.wholeCourse.citationCount++;
        if (!place?.context) {
            return;
        }
        const placeContext = place.context;
        const existing = entry.places.find((p) => p.context?.mode === placeContext.mode && p.context.entityId === placeContext.entityId);
        if (existing) {
            existing.citationCount++;
        } else {
            entry.places.push({ courseId: course.id, courseName: course.name, context: placeContext, name: place.name, citationCount: 1 });
        }
    };

    cited.sources.forEach((source) => count(source.course, { context: { mode: ChatServiceMode.LECTURE, entityId: source.lecture.id }, name: source.lecture.name }));
    cited.entitySources.forEach((source) => {
        if (source.course) {
            count(source.course, entityPlace(source));
        }
    });

    // Array.prototype.sort is stable, so equally cited entries keep the order they were cited in.
    const sorted = [...courses.values()].sort((a, b) => b.wholeCourse.citationCount - a.wholeCourse.citationCount);
    if (sorted.length === 0) {
        return undefined;
    }
    sorted.forEach((course) => course.places.sort((a, b) => b.citationCount - a.citationCount));
    const top = sorted[0];
    return {
        defaultTarget: top.places[0] ?? top.wholeCourse,
        courses: sorted,
        hasChoice: sorted.length > 1 || top.places.length > 1,
    };
}

/** The lecture or exercise an entity source opens a chat on, if it has one. */
function entityPlace(source: EntitySearchSource): { context: IrisHandoffTarget['context']; name: string } | undefined {
    if (source.entityId === undefined || !source.title) {
        return undefined;
    }
    if (source.entityType === 'lecture') {
        return { context: { mode: ChatServiceMode.LECTURE, entityId: source.entityId }, name: source.title };
    }
    const exerciseMode = source.entityType === 'exercise' ? EXERCISE_TYPE_TO_CHAT_MODE[source.exerciseType?.toLowerCase() ?? ''] : undefined;
    return exerciseMode ? { context: { mode: exerciseMode, entityId: source.entityId }, name: source.title } : undefined;
}

/**
 * The answer with its `[n]` citation markers rewritten into the Iris chat's own citations, so the chat shows the answer the
 * student saw, with citations that still open the same slides and video positions. Course information has no chat citation,
 * so it becomes a link to the same page its chip opens.
 */
export function chatAnswer(answer: string, cited: CitedSources): string {
    return convertCitationMarkers(answer, cited.sources.length + cited.entitySources.length, (sourceNumber) => {
        const lectureSource = citedLectureSource(cited, sourceNumber);
        if (lectureSource) {
            return lectureCitation(lectureSource);
        }
        const entitySource = citedEntitySource(cited, sourceNumber);
        return entitySource ? entityLink(entitySource) : '';
    });
}

/**
 * A chat citation of a lecture unit. The chat opens it with the same page or timestamp the answer card's chip uses, and labels
 * it with the unit's name; the server pins it to the current material version when the chat is created.
 */
function lectureCitation(source: LectureSearchResult): string {
    const unit = source.lectureUnit;
    const page = unit.queryParams['page'] ?? '';
    const timestamp = unit.queryParams['timestamp'] ?? '';
    return `[cite:L:${unit.id}:${page}:${timestamp}::${citationKeyword(unit.name)}:]`;
}

/** The unit's name as a citation keyword, which may not contain the citation's own separators. */
function citationKeyword(name: string): string {
    return name
        .replace(/[:[\]]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function entityLink(source: EntitySearchSource): string {
    const title = source.title?.replace(/[[\]\\]/g, (character) => `\\${character}`).trim();
    if (!title) {
        return '';
    }
    // An angle-bracket destination keeps parentheses and query strings in the link from ending it early.
    return source.link ? ` [${title}](<${source.link.replace(/[<>\s]/g, '')}>)` : ` (${title})`;
}
