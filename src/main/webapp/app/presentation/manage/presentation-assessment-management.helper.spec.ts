import { faLink, faUsers } from '@fortawesome/free-solid-svg-icons';
import { describe, expect, it } from 'vitest';

import { createPresentationSidebarData, hasResultPoints } from 'app/presentation/manage/presentation-assessment-management.helper';
import type { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';

describe('Presentation assessment management helpers', () => {
    const standalone: PresentationAssessment = {
        id: 2,
        title: 'Standalone',
    };
    const linked: PresentationAssessment = {
        id: 1,
        title: 'Linked',
        exerciseId: 5,
        exerciseTitle: 'Exercise',
    };

    it('should build grouped sidebar data', () => {
        const sidebar = createPresentationSidebarData([standalone, linked], 1, (key) => key, faUsers, faLink);

        expect(sidebar.pinnedData?.[0].routerLink).toBe('/course-management/1/presentations');
        expect(sidebar.groupedData?.standalone.entityData[0].subtitleLeft).toBeUndefined();
        expect(sidebar.groupedData?.linkedToExercise.entityData[0]).toEqual(
            expect.objectContaining({
                id: linked.id,
                routerLink: `/course-management/1/presentations/${linked.id}/exercises/${linked.exerciseId}`,
                subtitleLeft: 'Exercise',
                subtitleLeftIcon: faLink,
            }),
        );
        expect(hasResultPoints(0)).toBe(true);
        expect(hasResultPoints(null)).toBe(false);
    });
});
