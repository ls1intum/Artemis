import { ActivatedRouteSnapshot } from '@angular/router';

/** Chooses the detail audience from the existing route context, before loading the exercise. */
export function isExamExerciseRoute(route: ActivatedRouteSnapshot): boolean {
    // Imports load the selected source, while examId belongs to the destination.
    // Legacy import links without metadata represent course sources.
    if (route.pathFromRoot.some((ancestor) => ancestor.routeConfig?.path?.split('/').includes('import'))) {
        return route.queryParamMap.get('sourceIsExamExercise') === 'true';
    }
    return route.pathFromRoot.some((ancestor) => !!ancestor.params['examId']);
}
