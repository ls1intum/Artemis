import dayjs from 'dayjs/esm';

/**
 * Gets a list of semesters in the form 'WS18/19', 'SS18', ...
 * Starts from 2018 and goes one year into the future
 */
export function getSemesters() {
    // 2018 is the first year we offer semesters for and go one year into the future
    const startYear = 2018;
    const futureYears = 1;
    const years = dayjs().year() - startYear + futureYears;
    const startYearShort = startYear - 2000;

    const semesters: string[] = [];
    for (let i = 0; i <= years; i++) {
        const currentYear = startYearShort + years - i;
        semesters.push('WS' + currentYear + '/' + (currentYear + 1));
        semesters.push('SS' + currentYear);
    }
    // Add an empty semester as default value
    semesters.push('');
    return semesters;
}
