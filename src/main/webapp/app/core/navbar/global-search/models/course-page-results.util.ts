import { GlobalSearchResult } from 'app/openapi/model/global-search-result';

/** Internal type string for a course page offered by the palette itself; never sent to or returned by the server. */
export const COURSE_PAGE_TYPE = 'course_page';

interface CoursePage {
    /** Route segment below `/courses/:courseId`. */
    segment: string;
    /** i18n key of the page's name in the course navigation. */
    labelKey: string;
    /** Lower-case words a user may type for the page, in English and German, besides its translated name. */
    aliases: string[];
    /** Whether the page has an access rule (`COURSE_OVERVIEW_GUARDED_ROUTE_PATHS`), so it is offered only where the course allows it. */
    guarded?: true;
}

/**
 * The course pages the palette can offer, in the order of the course navigation. Pages without an access rule exist in
 * every course; a guarded page is offered only when the caller reports it as available for the course, so a match never
 * leads to a page the course does not offer to the user.
 */
const COURSE_PAGES: CoursePage[] = [
    { segment: 'exercises', labelKey: 'artemisApp.courseOverview.menu.exercises', aliases: ['exercises', 'tasks', 'übungen', 'aufgaben', 'hausaufgaben'] },
    {
        segment: 'statistics',
        labelKey: 'artemisApp.courseOverview.menu.statistics',
        aliases: ['statistics', 'grades', 'scores', 'points', 'progress', 'statistik', 'noten', 'punkte', 'fortschritt'],
    },
    { segment: 'lectures', labelKey: 'artemisApp.courseOverview.menu.lectures', aliases: ['lectures', 'slides', 'vorlesungen', 'folien'], guarded: true },
    { segment: 'exams', labelKey: 'artemisApp.courseOverview.menu.exams', aliases: ['exams', 'klausuren', 'prüfungen'], guarded: true },
    {
        segment: 'communication',
        labelKey: 'artemisApp.courseOverview.menu.communication',
        aliases: ['communication', 'messages', 'channels', 'chat', 'kommunikation', 'nachrichten', 'kanäle'],
        guarded: true,
    },
    {
        segment: 'tutorial-groups',
        labelKey: 'artemisApp.courseOverview.menu.tutorialGroups',
        aliases: ['tutorials', 'tutorial groups', 'tutorien', 'übungsgruppen'],
        guarded: true,
    },
    { segment: 'competencies', labelKey: 'artemisApp.courseOverview.menu.competencies', aliases: ['competencies', 'kompetenzen'], guarded: true },
    { segment: 'learning-path', labelKey: 'artemisApp.courseOverview.menu.learningPath', aliases: ['learning path', 'lernpfad'], guarded: true },
    { segment: 'faq', labelKey: 'artemisApp.courseOverview.menu.faq', aliases: ['faq', 'questions', 'fragen'], guarded: true },
    { segment: 'iris', labelKey: 'artemisApp.courseOverview.menu.iris', aliases: ['iris', 'assistant', 'assistent'], guarded: true },
    { segment: 'calendar', labelKey: 'artemisApp.courseOverview.menu.calendar', aliases: ['calendar', 'schedule', 'dates', 'kalender', 'termine'] },
];

/** Shortest query that is matched against page names; shorter prefixes would match almost every page. */
const MIN_PAGE_QUERY_LENGTH = 3;

/**
 * Returns the pages of one course whose name or one of whose aliases starts with the query, as results that open the page.
 *
 * @param query      the typed search text
 * @param courseId   the course the search is scoped to
 * @param courseName the course's title, shown on the result
 * @param translate  resolves an i18n key to the page name in the user's language
 * @param isAvailable whether a guarded page (by its route segment) is available in the course; guarded pages are left
 *                    out while this is unknown
 * @return the matching pages, in the order of the course navigation
 */
export function matchCoursePages(
    query: string,
    courseId: number,
    courseName: string | undefined,
    translate: (key: string) => string,
    isAvailable: (segment: string) => boolean = () => false,
): GlobalSearchResult[] {
    const normalized = query.trim().toLowerCase();
    if (normalized.length < MIN_PAGE_QUERY_LENGTH) {
        return [];
    }
    return COURSE_PAGES.filter((page) => !page.guarded || isAvailable(page.segment))
        .filter((page) => [translate(page.labelKey).toLowerCase(), ...page.aliases].some((name) => name.startsWith(normalized)))
        .map((page) => ({
            id: `course-page-${courseId}-${page.segment}`,
            type: COURSE_PAGE_TYPE,
            title: translate(page.labelKey),
            badgeKey: 'page',
            metadata: { courseId: String(courseId), courseName, link: `/courses/${courseId}/${page.segment}` },
        }));
}
