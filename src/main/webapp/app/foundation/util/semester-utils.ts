import dayjs from 'dayjs/esm';

const WINTER_SEMESTER_PATTERN = /^WS(\d{2})\/(\d{2})$/;
const SUMMER_SEMESTER_PATTERN = /^SS(\d{2})$/;

/**
 * Formats a year as the two-digit suffix the semester patterns above expect, so that a generated semester can be
 * parsed back by {@link getSemesterDateRange}. Only the years 2000 to 2009 need the padding, but every producer goes
 * through here so they cannot disagree: {@link getSemesters} looks its own output up with `indexOf`, and the course
 * date backfill in the database pads the same way.
 *
 * @param year the full year, e.g. 2026
 * @returns the two-digit suffix, e.g. '26'
 */
function twoDigitYear(year: number): string {
    return String(year - 2000).padStart(2, '0');
}

export interface SemesterDateRange {
    startDate: dayjs.Dayjs;
    endDate: dayjs.Dayjs;
}

/**
 * Maps a semester to the date range it spans.
 * Winter semester (WS): October 1 - March 31 of the following year
 * Summer semester (SS): April 1 - September 30
 *
 * @param semester the semester, e.g. 'WS25/26' or 'SS25'
 * @returns the range, or undefined when the semester does not follow the Artemis format
 */
export function getSemesterDateRange(semester: string | undefined): SemesterDateRange | undefined {
    if (!semester) {
        return undefined;
    }
    const winter = WINTER_SEMESTER_PATTERN.exec(semester);
    if (winter) {
        const startYearShort = Number(winter[1]);
        // the pair after the slash must be the following year; anything else is not a semester we can map
        if (Number(winter[2]) !== (startYearShort + 1) % 100) {
            return undefined;
        }
        const startYear = 2000 + startYearShort;
        return {
            startDate: dayjs(new Date(startYear, 9, 1)).startOf('day'),
            endDate: dayjs(new Date(startYear + 1, 2, 31)).endOf('day'),
        };
    }
    const summer = SUMMER_SEMESTER_PATTERN.exec(semester);
    if (summer) {
        const year = 2000 + Number(summer[1]);
        return {
            startDate: dayjs(new Date(year, 3, 1)).startOf('day'),
            endDate: dayjs(new Date(year, 8, 30)).endOf('day'),
        };
    }
    return undefined;
}

/**
 * Applies a newly selected semester's range to a course date pair, keeping any date the user set by hand.
 * A date follows the semester while it is empty or still exactly equal to the previously selected semester's
 * range; once it differs, it was set by hand and is returned unchanged.
 *
 * @param semester         the newly selected semester
 * @param previousSemester the semester that was selected before, or undefined on first selection
 * @param startDate        the current start date
 * @param endDate          the current end date
 * @returns the dates to use, with any hand-set value passed through unchanged
 */
export function applySemesterToDates(
    semester: string | undefined,
    previousSemester: string | undefined,
    startDate: dayjs.Dayjs | undefined,
    endDate: dayjs.Dayjs | undefined,
): { startDate: dayjs.Dayjs | undefined; endDate: dayjs.Dayjs | undefined } {
    const range = getSemesterDateRange(semester);
    if (!range) {
        return { startDate, endDate };
    }
    const previousRange = getSemesterDateRange(previousSemester);
    return {
        startDate: stillFollowsSemester(startDate, previousRange?.startDate) ? range.startDate : startDate,
        endDate: stillFollowsSemester(endDate, previousRange?.endDate) ? range.endDate : endDate,
    };
}

/**
 * @param value the current value of a date control
 * @param previouslyDerived the value the previously selected semester would have produced
 * @return true when the value was not set by hand and may be overwritten
 */
function stillFollowsSemester(value: dayjs.Dayjs | undefined, previouslyDerived: dayjs.Dayjs | undefined): boolean {
    if (!value) {
        return true;
    }
    return previouslyDerived !== undefined && value.isSame(previouslyDerived);
}

/**
 * Gets a list of semesters in the form 'WS18/19', 'SS18', ... in descending order.
 * Starts from 2018 and goes one year into the future.
 *
 * @param includeSemester a semester that must be selectable even when it predates the generated range,
 *                        for example when editing a course from before 2018. Appended last, which is the
 *                        oldest position in the descending list.
 */
export function getSemesters(includeSemester?: string): string[] {
    const startYear = 2018;
    const futureYears = 1;
    const years = dayjs().year() - startYear + futureYears;

    const semesters: string[] = [];
    for (let i = 0; i <= years; i++) {
        const currentYear = startYear + i;
        semesters.unshift(`SS${twoDigitYear(currentYear)}`);
        semesters.unshift(`WS${twoDigitYear(currentYear)}/${twoDigitYear(currentYear + 1)}`);
    }
    if (includeSemester && !semesters.includes(includeSemester)) {
        semesters.push(includeSemester);
    }
    return semesters;
}

/**
 * Gets the current semester based on the current date.
 * Winter semester (WS): October 1 - March 31
 * Summer semester (SS): April 1 - September 30
 */
export function getCurrentSemester(): string {
    const now = dayjs();
    const month = now.month(); // 0-indexed (0 = January)
    const year = now.year();

    // October (9) to December (11) -> WS of current/next year
    // January (0) to March (2) -> WS of previous/current year
    // April (3) to September (8) -> SS of current year
    if (month >= 9) {
        // October to December: WS starts
        return `WS${twoDigitYear(year)}/${twoDigitYear(year + 1)}`;
    } else if (month <= 2) {
        // January to March: WS continues
        return `WS${twoDigitYear(year - 1)}/${twoDigitYear(year)}`;
    } else {
        // April to September: SS
        return `SS${twoDigitYear(year)}`;
    }
}

/**
 * Gets the next semester after the current one.
 */
export function getNextSemester(): string {
    const now = dayjs();
    const month = now.month();
    const year = now.year();

    if (month >= 9) {
        // Currently WS (Oct-Dec), next is SS of next year
        return `SS${twoDigitYear(year + 1)}`;
    } else if (month <= 2) {
        // Currently WS (Jan-Mar), next is SS of current year
        return `SS${twoDigitYear(year)}`;
    } else {
        // Currently SS (Apr-Sep), next is WS
        return `WS${twoDigitYear(year)}/${twoDigitYear(year + 1)}`;
    }
}

/**
 * Gets the percentage of the current semester that has passed.
 * WS: Oct 1 - Mar 31 (6 months)
 * SS: Apr 1 - Sep 30 (6 months)
 */
export function getSemesterProgress(): number {
    const range = getSemesterDateRange(getCurrentSemester());
    if (!range) {
        return 0;
    }
    const now = dayjs();
    const totalDays = range.endDate.diff(range.startDate, 'day');
    const elapsedDays = now.diff(range.startDate, 'day');
    return Math.min(100, Math.max(0, (elapsedDays / totalDays) * 100));
}

/**
 * Gets the default semester for a new course request.
 * Returns the current semester if less than 50% complete, otherwise the next semester.
 */
export function getDefaultSemester(): string {
    const progress = getSemesterProgress();
    return progress < 50 ? getCurrentSemester() : getNextSemester();
}

/**
 * Gets a list of current and future semesters (no past semesters).
 * Includes the current semester and up to 2 years into the future.
 */
export function getCurrentAndFutureSemesters(): string[] {
    const currentSemester = getCurrentSemester();
    const futureYears = 2;
    const now = dayjs();
    const month = now.month();
    const currentYear = now.year();

    // Start from previous year if we're in Jan-Mar (winter semester spans previous/current year)
    const startYear = month <= 2 ? currentYear - 1 : currentYear;

    const semesters: string[] = [];

    // Generate semesters from start year through future years
    for (let i = 0; i <= futureYears + (month <= 2 ? 1 : 0); i++) {
        const year = startYear + i;
        semesters.push(`SS${twoDigitYear(year)}`);
        semesters.push(`WS${twoDigitYear(year)}/${twoDigitYear(year + 1)}`);
    }

    // Filter to only include current and future semesters
    const currentIndex = semesters.indexOf(currentSemester);
    if (currentIndex !== -1) {
        return semesters.slice(currentIndex);
    }

    return semesters;
}

/**
 * Generates a short name for a course based on the title and semester.
 * Extracts first letters from title words and appends semester digits.
 * Ensures minimum length of 3 characters.
 *
 * @param title the course title
 * @param semester the semester (e.g., "WS25/26", "SS25")
 * @returns a generated short name
 */
export function generateCourseShortName(title: string, semester: string): string {
    let shortName = '';

    // Extract first letters from title words (only alphanumeric characters)
    if (title?.trim()) {
        const words = title.trim().split(/\s+/);
        for (const word of words) {
            if (word.length > 0) {
                const firstChar = word.charAt(0).toUpperCase();
                if (/[A-Z0-9]/i.test(firstChar)) {
                    shortName += firstChar;
                }
            }
        }
    }

    // Extract all digits from semester (e.g., "WS25/26" -> "2526", "SS25" -> "25")
    if (semester) {
        const digits = semester.replace(/\D/g, '');
        if (digits) {
            shortName += digits;
        }
    }

    // Ensure minimum length of 3 characters
    if (shortName.length < 3) {
        shortName += 'CRS'.substring(0, 3 - shortName.length);
    }

    return shortName;
}
