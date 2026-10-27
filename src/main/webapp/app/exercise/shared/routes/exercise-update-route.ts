import { ResolveData, Route } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';

interface ExerciseUpdateRouteConfig {
    loadComponent: NonNullable<Route['loadComponent']>;
    pageTitle: string;
    resolve?: ResolveData;
}

/** Shares the update component, resolver and editor access policy between course and exam routes.
 * Paths, title overrides and leaving guards stay with the caller because those differ between contexts.
 */
export function createExerciseUpdateRoute(config: ExerciseUpdateRouteConfig): (path: string, pageTitle?: string, canDeactivate?: Route['canDeactivate']) => Route {
    return (path, pageTitle = config.pageTitle, canDeactivate) => {
        const route: Route = {
            path,
            loadComponent: config.loadComponent,
            data: { authorities: IS_AT_LEAST_EDITOR, pageTitle },
            canActivate: [UserRouteAccessService],
        };
        if (config.resolve) {
            route.resolve = config.resolve;
        }
        if (canDeactivate) {
            route.canDeactivate = canDeactivate;
        }
        return route;
    };
}
