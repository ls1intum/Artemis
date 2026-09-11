import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';

import { User } from 'app/account/user/user.model';
import type { SidebarCardElement, SidebarData } from 'app/foundation/types/sidebar';
import type { PresentationAssessment, PresentationAssessmentInstance } from 'app/presentation/shared/entities/presentation-assessment.model';

export type PresentationViewMode = 'presentations' | 'students';
export type AssessmentStatusFilter = 'all' | 'assessed' | 'pending';
export type PresentationTypeFilter = 'all' | 'standalone' | 'exercise';

export interface FilterOption<T> {
    label: string;
    value: T;
}

export interface PresentationStudentRow {
    studentLogin: string;
    student: User;
    presentationAssessment: PresentationAssessment;
    instance: PresentationAssessmentInstance;
}

export type SelectedPresentationStudentRow = PresentationStudentRow;

export interface StudentRowFilters {
    query: string;
    status: AssessmentStatusFilter;
    presentation: number | 'all';
    type: PresentationTypeFilter;
    sortField: string;
    sortOrder: number;
}

export function createStudentRows(presentationAssessments: PresentationAssessment[], courseStudents: User[]): PresentationStudentRow[] {
    const studentsByLogin = mapStudentsByLogin(courseStudents);
    return presentationAssessments.flatMap((presentationAssessment) =>
        (presentationAssessment.instances ?? []).flatMap((instance) =>
            (instance.studentLogins ?? []).map((studentLogin) => ({
                studentLogin,
                student: studentsByLogin.get(studentLogin) ?? new User(undefined, studentLogin),
                presentationAssessment,
                instance,
            })),
        ),
    );
}

export function createSelectedStudentRows(presentationAssessment: PresentationAssessment | undefined, courseStudents: User[]): SelectedPresentationStudentRow[] {
    if (!presentationAssessment) {
        return [];
    }
    return createStudentRows([presentationAssessment], courseStudents);
}

export function filterAndSortStudentRows(rows: PresentationStudentRow[], filters: StudentRowFilters): PresentationStudentRow[] {
    const query = filters.query.trim().toLocaleLowerCase();
    return rows.filter((row) => matchesStudentRow(row, query, filters)).sort((first, second) => compareStudentRows(first, second, filters.sortField) * filters.sortOrder);
}

export function filterStudentRowsBySearch(rows: PresentationStudentRow[], searchTerm: string): PresentationStudentRow[] {
    const query = searchTerm.trim().toLocaleLowerCase();
    return query ? rows.filter((row) => matchesStudentQuery(row, query)) : rows;
}

export function createPresentationSidebarData(
    presentationAssessments: PresentationAssessment[],
    viewMode: PresentationViewMode,
    selectedPresentationId: number | undefined,
    translate: (key: string) => string,
    overviewIcon: IconDefinition,
    linkIcon: IconDefinition,
): SidebarData {
    const overview: SidebarCardElement = {
        id: 'overview',
        title: translate('artemisApp.presentationAssessment.overallOverview'),
        icon: overviewIcon,
        size: 'M',
        active: viewMode === 'students',
        disableNavigation: true,
    };
    const standalonePresentations = presentationAssessments
        .filter((assessment) => !assessment.exerciseId)
        .sort((first, second) => (first.title ?? '').localeCompare(second.title ?? ''));
    const linkedPresentations = presentationAssessments
        .filter((assessment) => !!assessment.exerciseId)
        .sort((first, second) => (first.exerciseTitle ?? '').localeCompare(second.exerciseTitle ?? '') || (first.title ?? '').localeCompare(second.title ?? ''));
    return {
        groupByCategory: true,
        sidebarType: 'default',
        storageId: 'presentationAssessment',
        pinnedData: [overview],
        groupedData: {
            standalone: {
                entityData: standalonePresentations.map((assessment) => toSidebarItem(assessment, viewMode, selectedPresentationId)),
                isHideCount: true,
                translationKey: 'artemisApp.presentationAssessment.standalone',
            },
            linkedToExercise: {
                entityData: linkedPresentations.map((assessment) => toSidebarItem(assessment, viewMode, selectedPresentationId, linkIcon)),
                isHideCount: true,
                translationKey: 'artemisApp.presentationAssessment.linkedToExercise',
            },
        },
    };
}

export function resolveStudentsByLogin(courseStudents: User[], studentLogins: string[]): User[] {
    const studentsByLogin = mapStudentsByLogin(courseStudents);
    return studentLogins.map((login) => studentsByLogin.get(login) ?? new User(undefined, login));
}

export function hasResultPoints(resultPoints: number | null | undefined): resultPoints is number {
    return resultPoints !== undefined && resultPoints !== null;
}

function mapStudentsByLogin(students: User[]): Map<string, User> {
    return new Map(students.filter((student) => !!student.login).map((student) => [student.login!, student]));
}

function matchesStudentRow(row: PresentationStudentRow, query: string, filters: StudentRowFilters): boolean {
    const matchesQuery = !query || matchesStudentQuery(row, query, true);
    const matchesStatus = filters.status === 'all' || (filters.status === 'assessed' ? hasResultPoints(row.instance.resultPoints) : !hasResultPoints(row.instance.resultPoints));
    const matchesPresentation = filters.presentation === 'all' || row.presentationAssessment.id === filters.presentation;
    const matchesType = filters.type === 'all' || (filters.type === 'exercise' ? !!row.presentationAssessment.exerciseId : !row.presentationAssessment.exerciseId);
    return matchesQuery && matchesStatus && matchesPresentation && matchesType;
}

function matchesStudentQuery(row: PresentationStudentRow, query: string, includePresentation = false): boolean {
    const values = [row.studentLogin, row.student.name, row.student.firstName, row.student.lastName, row.student.email];
    if (includePresentation) {
        values.push(row.presentationAssessment.title);
    }
    return values.filter((value): value is string => !!value).some((value) => value.toLocaleLowerCase().includes(query));
}

function compareStudentRows(first: PresentationStudentRow, second: PresentationStudentRow, field: string): number {
    const firstValue = studentSortValue(first, field);
    const secondValue = studentSortValue(second, field);
    if (firstValue === secondValue) {
        return 0;
    }
    if (firstValue === undefined) {
        return 1;
    }
    if (secondValue === undefined) {
        return -1;
    }
    return firstValue < secondValue ? -1 : 1;
}

function studentSortValue(row: PresentationStudentRow, field: string): string | number | undefined {
    switch (field) {
        case 'studentLogin':
            return row.studentLogin.toLocaleLowerCase();
        case 'presentationTitle':
            return row.presentationAssessment.title?.toLocaleLowerCase();
        case 'presentationDate':
            return row.instance.presentationDate?.valueOf();
        case 'resultPoints':
            return row.instance.resultPoints ?? undefined;
        default:
            return undefined;
    }
}

function toSidebarItem(
    assessment: PresentationAssessment,
    viewMode: PresentationViewMode,
    selectedPresentationId: number | undefined,
    linkIcon?: IconDefinition,
): SidebarCardElement {
    return {
        id: assessment.id!,
        title: assessment.title ?? '',
        subtitleLeft: linkIcon ? assessment.exerciseTitle : undefined,
        subtitleLeftIcon: linkIcon,
        size: 'M',
        active: viewMode === 'presentations' && selectedPresentationId === assessment.id,
        disableNavigation: true,
    };
}
