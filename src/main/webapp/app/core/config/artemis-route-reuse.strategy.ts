import { ActivatedRouteSnapshot, BaseRouteReuseStrategy, Params } from '@angular/router';

/**
 * Custom RouteReuseStrategy for Artemis.
 *
 * Angular's default strategy reuses components whenever the matched routeConfig is identical
 * (e.g. `/course-management/:courseId/exams/:examId/grading`). In most of the app, this is desirable.
 * However, when navigating between entities via navigation sidebars (e.g. Exam 1 -> Exam 2 in the exam sidebar),
 * reusing the component instance causes state pollution, un-reset signals/forms, and stale child components.
 *
 * To avoid affecting the entire application, this strategy only checks parameter equality for routes
 * (or ancestor routes) that explicitly opt in via the `dontReuseOnParamChange: true` (or `reuseOnParamChange: false`)
 * route data flag. For all other routes, it defaults to Angular's standard reuse behavior.
 */
export class ArtemisRouteReuseStrategy extends BaseRouteReuseStrategy {
    override shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
        if (this.hasReuseDisabledOnParamChange(future)) {
            return super.shouldReuseRoute(future, curr) && this.paramsEqual(future.params, curr.params);
        }

        return super.shouldReuseRoute(future, curr);
    }

    private hasReuseDisabledOnParamChange(snapshot: ActivatedRouteSnapshot): boolean {
        let current: ActivatedRouteSnapshot | null = snapshot;
        while (current) {
            if (current.data?.dontReuseOnParamChange || current.data?.reuseOnParamChange === false) {
                return true;
            }
            current = current.parent;
        }
        return false;
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
