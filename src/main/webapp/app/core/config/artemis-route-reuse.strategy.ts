import { ActivatedRouteSnapshot, BaseRouteReuseStrategy, Params } from '@angular/router';

/**
 * Custom RouteReuseStrategy for Artemis.
 *
 * Angular's default strategy reuses components whenever the matched routeConfig is identical
 * (e.g. `/course-management/:courseId/exams/:examId/grading`). When navigating between different
 * entities (e.g. Exam 1 -> Exam 2), reusing the component instance causes state pollution,
 * un-reset signals/forms, and stale child components.
 *
 * This strategy ensures that when route parameters (such as `examId`) differ,
 * the component instance is not reused, allowing clean lifecycle initialization (fresh component).
 */
export class ArtemisRouteReuseStrategy extends BaseRouteReuseStrategy {
    override shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
        return future.routeConfig === curr.routeConfig && this.paramsEqual(future.params, curr.params);
    }

    private paramsEqual(paramsA?: Params, paramsB?: Params): boolean {
        if (paramsA === paramsB) {
            return true;
        }
        const a = paramsA ?? {};
        const b = paramsB ?? {};
        const keysA = Object.keys(a);
        const keysB = Object.keys(b);
        if (keysA.length !== keysB.length) {
            return false;
        }
        return keysA.every((key) => a[key] === b[key]);
    }
}
