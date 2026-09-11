import { faLink, faUsers } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { describe, expect, it } from 'vitest';

import { User } from 'app/account/user/user.model';
import {
    createPresentationSidebarData,
    createSelectedStudentRows,
    createStudentRows,
    filterAndSortStudentRows,
    filterStudentRowsBySearch,
    hasResultPoints,
    resolveStudentsByLogin,
} from 'app/presentation/manage/presentation-assessment-management.helper';
import type { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';

describe('Presentation assessment management helpers', () => {
    const students = [
        Object.assign(new User(), { login: 'anna', name: 'Anna Student', email: 'anna@example.com' }),
        Object.assign(new User(), { login: 'bert', name: 'Bert Student', email: 'bert@example.com' }),
    ];
    const standalone: PresentationAssessment = {
        id: 2,
        title: 'Standalone',
        instances: [{ id: 20, presentationDate: dayjs('2026-02-02'), resultPoints: 8, studentLogins: ['bert'] }],
    };
    const linked: PresentationAssessment = {
        id: 1,
        title: 'Linked',
        exerciseId: 5,
        exerciseTitle: 'Exercise',
        instances: [{ id: 10, presentationDate: dayjs('2026-01-01'), studentLogins: ['anna', 'unknown'] }],
    };

    it('should create rows and preserve unknown logins', () => {
        const rows = createStudentRows([linked], students);

        expect(rows.map((row) => row.studentLogin)).toEqual(['anna', 'unknown']);
        expect(rows[0].student).toBe(students[0]);
        expect(rows[1].student.login).toBe('unknown');
        expect(createSelectedStudentRows(undefined, students)).toEqual([]);
    });

    it('should filter and sort rows independently by all supported filters', () => {
        const rows = createStudentRows([standalone, linked], students);
        const baseFilters = { query: '', status: 'all' as const, presentation: 'all' as const, type: 'all' as const, sortField: 'studentLogin', sortOrder: 1 };

        expect(filterAndSortStudentRows(rows, baseFilters).map((row) => row.studentLogin)).toEqual(['anna', 'bert', 'unknown']);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, query: 'BERT' }).map((row) => row.studentLogin)).toEqual(['bert']);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, status: 'assessed' }).map((row) => row.studentLogin)).toEqual(['bert']);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, status: 'pending' })).toHaveLength(2);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, presentation: 1 })).toHaveLength(2);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, type: 'standalone' })).toHaveLength(1);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, type: 'exercise' })).toHaveLength(2);
        expect(filterAndSortStudentRows(rows, { ...baseFilters, sortField: 'presentationDate', sortOrder: -1 })[0].studentLogin).toBe('bert');
        expect(filterAndSortStudentRows(rows, { ...baseFilters, sortField: 'resultPoints', sortOrder: 1 })[0].studentLogin).toBe('bert');
        expect(filterAndSortStudentRows(rows, { ...baseFilters, sortField: 'resultPoints', sortOrder: -1 }).map((row) => row.studentLogin)).toEqual(['bert', 'anna', 'unknown']);
        expect(filterStudentRowsBySearch(rows, 'Anna Student').map((row) => row.studentLogin)).toEqual(['anna']);
        expect(filterStudentRowsBySearch(rows, 'bert@example.com').map((row) => row.studentLogin)).toEqual(['bert']);
    });

    it('should build grouped sidebar data and resolve assigned students', () => {
        const sidebar = createPresentationSidebarData([standalone, linked], 'presentations', linked.id, (key) => key, faUsers, faLink);

        expect(sidebar.pinnedData?.[0].active).toBe(false);
        expect(sidebar.groupedData?.standalone.entityData[0].subtitleLeft).toBeUndefined();
        expect(sidebar.groupedData?.linkedToExercise.entityData[0]).toEqual(
            expect.objectContaining({ id: linked.id, active: true, subtitleLeft: 'Exercise', subtitleLeftIcon: faLink }),
        );
        expect(resolveStudentsByLogin(students, ['anna', 'unknown']).map((student) => student.login)).toEqual(['anna', 'unknown']);
        expect(hasResultPoints(0)).toBe(true);
        expect(hasResultPoints(null)).toBe(false);
    });
});
