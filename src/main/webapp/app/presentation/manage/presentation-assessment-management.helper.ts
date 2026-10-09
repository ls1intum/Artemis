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

export function createPresentationSidebarData(
    presentationAssessments: PresentationAssessment[],
    courseId: number,
    translate: (key: string) => string,
    overviewIcon: IconDefinition,
    linkIcon: IconDefinition,
): SidebarData {
    const overview: SidebarCardElement = {
        id: 'overview',
        title: translate('artemisApp.presentationAssessment.overallOverview'),
        icon: overviewIcon,
        size: 'M',
        routerLink: `/course-management/${courseId}/presentations`,
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
                entityData: standalonePresentations.map((assessment) => toSidebarItem(assessment, courseId)),
                isHideCount: true,
                translationKey: 'artemisApp.presentationAssessment.standalone',
            },
            linkedToExercise: {
                entityData: linkedPresentations.map((assessment) => toSidebarItem(assessment, courseId, linkIcon)),
                isHideCount: true,
                translationKey: 'artemisApp.presentationAssessment.linkedToExercise',
            },
        },
    };
}

export function hasResultPoints(resultPoints: number | null | undefined): resultPoints is number {
    return resultPoints !== undefined && resultPoints !== null;
}

function toSidebarItem(assessment: PresentationAssessment, courseId: number, linkIcon?: IconDefinition): SidebarCardElement {
    return {
        id: assessment.id!,
        title: assessment.title ?? '',
        subtitleLeft: linkIcon ? assessment.exerciseTitle : undefined,
        subtitleLeftIcon: linkIcon,
        size: 'M',
        routerLink: `/course-management/${courseId}/presentations/${assessment.id}${assessment.exerciseId ? `/exercises/${assessment.exerciseId}` : ''}`,
    };
}
