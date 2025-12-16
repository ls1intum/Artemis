import dayjs from 'dayjs/esm';

/**
 * Gets a list of semesters in the form 'WS18/19', 'SS18', ...
 * Starts from 2018 and goes one year into the future
 */
export function getSemesters() {
    const startYear = 2018;
    const futureYears = 1;
    const years = dayjs().year() - startYear + futureYears;
    const startYearShort = startYear - 2000;

    const semesters: string[] = [];
    for (let i = 0; i <= years; i++) {
        const currentYear = startYearShort + i;
        semesters.unshift('SS' + currentYear);
        semesters.unshift('WS' + currentYear + '/' + (currentYear + 1));
    }
    // Add an empty semester as default value
    semesters.push('');
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
    const yearShort = year - 2000;

    // October (9) to December (11) -> WS of current/next year
    // January (0) to March (2) -> WS of previous/current year
    // April (3) to September (8) -> SS of current year
    if (month >= 9) {
        // October to December: WS starts
        return `WS${yearShort}/${yearShort + 1}`;
    } else if (month <= 2) {
        // January to March: WS continues
        return `WS${yearShort - 1}/${yearShort}`;
    } else {
        // April to September: SS
        return `SS${yearShort}`;
    }
}

/**
 * Gets the next semester after the current one.
 */
export function getNextSemester(): string {
    const now = dayjs();
    const month = now.month();
    const year = now.year();
    const yearShort = year - 2000;

    if (month >= 9) {
        // Currently WS (Oct-Dec), next is SS of next year
        return `SS${yearShort + 1}`;
    } else if (month <= 2) {
        // Currently WS (Jan-Mar), next is SS of current year
        return `SS${yearShort}`;
    } else {
        // Currently SS (Apr-Sep), next is WS
        return `WS${yearShort}/${yearShort + 1}`;
    }
}

/**
 * Gets the percentage of the current semester that has passed.
 * WS: Oct 1 - Mar 31 (6 months)
 * SS: Apr 1 - Sep 30 (6 months)
 */
export function getSemesterProgress(): number {
    const now = dayjs();
    const month = now.month();
    const year = now.year();

    let semesterStart: dayjs.Dayjs;
    let semesterEnd: dayjs.Dayjs;

    if (month >= 9) {
        // Oct-Dec of current year
        semesterStart = dayjs(new Date(year, 9, 1)); // Oct 1
        semesterEnd = dayjs(new Date(year + 1, 2, 31)); // Mar 31 next year
    } else if (month <= 2) {
        // Jan-Mar, semester started previous year
        semesterStart = dayjs(new Date(year - 1, 9, 1)); // Oct 1 prev year
        semesterEnd = dayjs(new Date(year, 2, 31)); // Mar 31
    } else {
        // Apr-Sep
        semesterStart = dayjs(new Date(year, 3, 1)); // Apr 1
        semesterEnd = dayjs(new Date(year, 8, 30)); // Sep 30
    }

    const totalDays = semesterEnd.diff(semesterStart, 'day');
    const elapsedDays = now.diff(semesterStart, 'day');

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
    const yearShort = currentYear - 2000;

    // Start from previous year if we're in Jan-Mar (winter semester spans previous/current year)
    const startYear = month <= 2 ? yearShort - 1 : yearShort;

    const semesters: string[] = [];

    // Generate semesters from start year through future years
    for (let i = 0; i <= futureYears + (month <= 2 ? 1 : 0); i++) {
        const year = startYear + i;
        semesters.push(`SS${year}`);
        semesters.push(`WS${year}/${year + 1}`);
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
