import { ActivatedRouteSnapshot } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { isExamExerciseRoute } from './exercise-detail-route-context';

describe('exercise detail route context', () => {
    it.each([
        ['edit', true, 'false', true],
        ['details', false, 'true', false],
        ['import', true, 'false', false],
        ['import', false, 'true', true],
        ['import', true, undefined, false],
        ['import', false, 'false', false],
    ])('selects %s with target exam %s and retained source flag %s', (purpose, targetIsExam, sourceFlag, expected) => {
        const ancestor = new ActivatedRouteSnapshot();
        ancestor.params = targetIsExam ? { examId: 7 } : {};
        const route = new ActivatedRouteSnapshot();
        route.params = { exerciseId: 123 };
        route.queryParams = sourceFlag === undefined ? {} : { sourceIsExamExercise: sourceFlag };
        vi.spyOn(route, 'routeConfig', 'get').mockReturnValue({ path: `exercises/:exerciseId/${purpose}` });
        vi.spyOn(route, 'pathFromRoot', 'get').mockReturnValue([ancestor, route]);
        expect(isExamExerciseRoute(route)).toBe(expected);
    });
});
