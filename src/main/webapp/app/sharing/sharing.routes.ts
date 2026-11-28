import { Injector, Provider, inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { MODULE_FEATURE_SHARING } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IS_AT_LEAST_EDITOR } from 'app/foundation/constants/authority.constants';
import { PROGRAMMING_EXERCISE_EXPORT_ACTION } from 'app/programming/api/programming-exercise-extensions';
import { defer, of, switchMap } from 'rxjs';

function sharingEnabled() {
    return inject(ProfileService).isModuleFeatureActive(MODULE_FEATURE_SHARING);
}

export function sharingGuard() {
    return sharingEnabled() || inject(Router).createUrlTree(['/courses']);
}

export const sharingCourseRoutes: Routes = [
    {
        path: 'programming-exercises/import-from-sharing',
        canMatch: [sharingGuard],
        data: { authorities: IS_AT_LEAST_EDITOR, preload: 'never' },
        loadChildren: () => import('./sharing-import.route').then((m) => m.routes),
    },
];

export const sharingExportProvider: Provider = {
    provide: PROGRAMMING_EXERCISE_EXPORT_ACTION,
    useFactory: () => {
        if (!sharingEnabled()) return of(undefined);
        const injector = inject(Injector);
        return defer(() => import('./programming-exercise-sharing.service')).pipe(switchMap((m) => injector.get(m.ProgrammingExerciseSharingService).getExportAction()));
    },
};
