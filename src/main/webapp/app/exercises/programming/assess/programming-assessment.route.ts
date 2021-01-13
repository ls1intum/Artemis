import { Injectable, NgModule } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';

@Injectable({ providedIn: 'root' })
export class NewStudentParticipationResolver implements Resolve<number | undefined> {
    constructor(private programmingSubmissionService: ProgrammingSubmissionService, private jhiAlertService: JhiAlertService) {}

    /**
     * Resolves the needed studentParticipationId for the CodeEditorTutorAssessmentContainerComponent for submissions without assessment
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        const exerciseId = Number(route.paramMap.get('exerciseId'));
        const correctionRound = Number(route.queryParamMap.get('correction-round'));

        if (exerciseId) {
            const returnValue = this.programmingSubmissionService.getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId, true, correctionRound).pipe(
                map((submission) => submission.participation!.id!),
                catchError((error) => {
                    if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                        this.jhiAlertService.error('artemisApp.submission.lockedSubmissionsLimitReached');
                    }
                    return Observable.of(error);
                }),
            );
            return returnValue;
        }
        return Observable.of(undefined);
    }
}

@Injectable({ providedIn: 'root' })
export class StudentParticipationResolver implements Resolve<number | undefined> {
    constructor(private programmingSubmissionService: ProgrammingSubmissionService, private jhiAlertService: JhiAlertService) {}

    /**
     *
     * Locks the latest submission of a programming exercises participation, if it is not already locked
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        const participationId = Number(route.paramMap.get('participationId'));
        const correctionRound = Number(route.queryParamMap.get('correction-round'));

        if (participationId) {
            return this.programmingSubmissionService.lockAndGetProgrammingSubmissionParticipation(participationId, correctionRound).pipe(
                map((participation) => participation.id),
                catchError((error) => {
                    if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                        this.jhiAlertService.error('artemisApp.submission.lockedSubmissionsLimitReached');
                    }
                    return Observable.of(error);
                }),
            );
        }
        return Observable.of(undefined);
    }
}

export const routes: Routes = [
    {
        path: ':courseId/programming-exercises/:exerciseId/code-editor/new/assessment',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        resolve: {
            studentParticipationId: NewStudentParticipationResolver,
        },
        runGuardsAndResolvers: 'always',
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/code-editor/:participationId/assessment',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        resolve: {
            studentParticipationId: StudentParticipationResolver,
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingAssessmentRoutingModule {}
