import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { convertCitationMarkers } from 'app/core/navbar/global-search/util/iris-citation-markers.util';
import { CitedSources, citedEntitySource, citedLectureSource } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { ChatServiceMode, EXERCISE_TYPE_TO_CHAT_MODE } from 'app/iris/shared/entities/iris-session-context.model';

/** The place a global search answer is continued in: a course's Iris chat, optionally on one of its lectures or exercises. */
export interface IrisHandoffTarget {
    courseId: number;
    /** The lecture or exercise the chat starts on; undefined for the course itself. */
    context?: { mode: ChatServiceMode; entityId: number };
    /** The lecture or exercise, or the course when there is none. */
    name: string;
}

interface Place {
    context?: IrisHandoffTarget['context'];
    name: string;
}

interface Counted<T> {
    value: T;
    citations: number;
}

/**
 * The place the answer's sources point to most: the most cited lecture or exercise of the most cited course, or that course
 * itself. A lecture source belongs to its lecture, an exercise with an Iris chat to its exercise, a lecture entry to its
 * lecture, and every other source (FAQs, exams, channels, course entries) only to its course. Ties keep the order the sources
 * were cited in.
 *
 * @returns the target, or undefined when no source names a course
 */
export function handoffTarget(cited: CitedSources): IrisHandoffTarget | undefined {
    const courses = new Map<number, Counted<{ id: number; name: string; places: Counted<Place>[] }>>();
    const count = (course: { id: number; name: string }, place?: Place) => {
        let entry = courses.get(course.id);
        if (!entry) {
            entry = { value: { id: course.id, name: course.name, places: [] }, citations: 0 };
            courses.set(course.id, entry);
        }
        entry.citations++;
        if (!place?.context) {
            return;
        }
        const placeContext = place.context;
        const existing = entry.value.places.find((p) => p.value.context?.mode === placeContext.mode && p.value.context.entityId === placeContext.entityId);
        if (existing) {
            existing.citations++;
        } else {
            entry.value.places.push({ value: place, citations: 1 });
        }
    };

    // In marker order, resolved like the answer card numbers its chips, so a tie goes to whichever source was cited first.
    const sourceCount = cited.sources.length + cited.entitySources.length;
    for (let sourceNumber = 1; sourceNumber <= sourceCount; sourceNumber++) {
        const lectureSource = citedLectureSource(cited, sourceNumber);
        if (lectureSource) {
            count(lectureSource.course, { context: { mode: ChatServiceMode.LECTURE, entityId: lectureSource.lecture.id }, name: lectureSource.lecture.name });
            continue;
        }
        const entitySource = citedEntitySource(cited, sourceNumber);
        if (entitySource?.course) {
            count(entitySource.course, entityPlace(entitySource));
        }
    }

    const course = mostCited([...courses.values()]);
    if (!course) {
        return undefined;
    }
    const place = mostCited(course.places);
    return place ? { courseId: course.id, context: place.context, name: place.name } : { courseId: course.id, name: course.name };
}

/** Array.prototype.sort is stable, so equally cited entries keep the order they were cited in. */
function mostCited<T>(entries: Counted<T>[]): T | undefined {
    return [...entries].sort((a, b) => b.citations - a.citations)[0]?.value;
}

/** The lecture or exercise an entity source opens a chat on, if it has one. */
function entityPlace(source: EntitySearchSource): Place | undefined {
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

/** Longest summary a handed-off citation carries; the chat shows it in the citation's hover card. */
const CITATION_SUMMARY_MAX_LENGTH = 200;

/**
 * A chat citation of a lecture unit. The chat opens it with the same page or timestamp the answer card's chip uses, and labels
 * it with the unit's name; the server pins it to the current material version when the chat is created. Its summary is the
 * cited passage, so hovering it shows what the answer drew on, like a citation Iris writes in the chat.
 */
function lectureCitation(source: LectureSearchResult): string {
    const unit = source.lectureUnit;
    const page = unit.queryParams['page'] ?? '';
    const timestamp = unit.queryParams['timestamp'] ?? '';
    const keyword = citationKeyword(unit.name);
    return `[cite:L:${unit.id}:${page}:${timestamp}::${keyword}:${citationSummary(source.snippet) || keyword}]`;
}

/**
 * The cited passage as a citation summary: one line, without the brackets that would end the citation, and short enough for a
 * hover card. A trailing token that reads like a version field is set apart, so it cannot pass for the material version.
 */
function citationSummary(snippet?: string): string {
    const text = (snippet ?? '').replace(/\[/g, '(').replace(/]/g, ')').replace(/\s+/g, ' ').trim();
    const short = text.length <= CITATION_SUMMARY_MAX_LENGTH ? text : `${text.slice(0, CITATION_SUMMARY_MAX_LENGTH).replace(/\s+\S*$/, '')}…`;
    return short.replace(/:(v[at]\d+)$/, ': $1');
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
