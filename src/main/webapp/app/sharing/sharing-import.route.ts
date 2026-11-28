import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, RedirectCommand, Router, Routes } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import type { ProgrammingExerciseImport } from 'app/programming/api/programming-exercise-extensions';
import { externalProgrammingExerciseImportRoute } from 'app/programming/api/programming-exercise-import.route';
import { catchError, map, of, tap } from 'rxjs';
import { ProgrammingExerciseSharingService } from './programming-exercise-sharing.service';
import { SharingInfo } from './sharing.model';

export function resolveSharingImport(route: ActivatedRouteSnapshot) {
    const service = inject(ProgrammingExerciseSharingService);
    const alerts = inject(AlertService);
    const router = inject(Router);
    const info = new SharingInfo();
    const query = route.queryParamMap;
    info.basketToken = query.get('basketToken') ?? '';
    info.returnURL = query.get('returnURL') ?? '';
    info.apiBaseURL = query.get('apiBaseURL') ?? '';
    info.checksum = query.get('checksum') ?? '';
    info.exercisePosition = Number(query.get('selectedExercise'));

    return service.loadDetailsForExercises(info).pipe(
        map((exercise): ProgrammingExerciseImport => {
            return {
                exercise,
                save: (editedExercise, courseId) =>
                    service
                        .setUpFromSharingImport(editedExercise, courseId, info)
                        .pipe(tap(() => alerts.success('artemisApp.programmingExercise.created', { param: editedExercise.title }))),
            };
        }),
        catchError((error) => {
            alerts.error('artemisApp.sharing.error.loadingDetails', { message: error?.message || error?.error?.message || 'Unknown error occurred' });
            return of(new RedirectCommand(router.createUrlTree(['/course-management', route.params['courseId'], 'exercises'])));
        }),
    );
}

export const routes: Routes = [externalProgrammingExerciseImportRoute(resolveSharingImport)];
