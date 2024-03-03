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
