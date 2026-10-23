import { Route } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { featureToggleGuard } from 'app/foundation/feature-toggle/feature-toggle.guard';
import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';
import { createExerciseUpdateRoute } from 'app/exercise/shared/routes/exercise-update-route';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';

export const programmingExerciseUpdateRoute = createExerciseUpdateRoute({
    loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
    pageTitle: 'artemisApp.programmingExercise.home.title',
    resolve: { programmingExercise: ProgrammingExerciseResolve },
});

export function programmingExerciseDetailRoute(path: string): Route {
    return {
        path,
        loadComponent: () => import('app/programming/manage/detail/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    };
}

export function programmingExerciseVersionHistoryRoute(path: string): Route {
    return {
        path,
        loadComponent: () =>
            import('app/programming/manage/version-history/programming-exercise-version-history.component').then((m) => m.ProgrammingExerciseVersionHistoryComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exercise.versionHistory.title',
        },
        canActivate: [UserRouteAccessService],
    };
}

export function programmingExerciseBuildPlanRoute(path: string): Route {
    return {
        path,
        loadComponent: () => import('app/programming/manage/build-plan-editor/build-plan-editor-page.component').then((m) => m.BuildPlanEditorPageComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.buildPlanEditor',
        },
        // the editor is a programming-exercise feature, so a direct URL must not load it when the feature is toggled off
        canActivate: [UserRouteAccessService, featureToggleGuard(FeatureToggle.ProgrammingExercises)],
        canDeactivate: [PendingChangesGuard],
    };
}

export function programmingExerciseGradingRoute(path: string): Route {
    return {
        path,
        loadComponent: () =>
            import('app/programming/manage/grading/configure/programming-exercise-configure-grading.component').then((m) => m.ProgrammingExerciseConfigureGradingComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    };
}
