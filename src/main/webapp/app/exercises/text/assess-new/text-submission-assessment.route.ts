import { Injectable } from '@angular/core';
import { Routes, Resolve, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Location } from '@angular/common';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextSubmissionAssessmentComponent } from './text-submission-assessment.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';

@Injectable({ providedIn: 'root' })
export class StudentParticipationResolver implements Resolve<StudentParticipation | null> {
    constructor(private textAssessmentsService: TextAssessmentsService) {}

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentsService.
     * @param route
     * @param state
     */
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const submissionId = Number(route.paramMap.get('submissionId'));
        console.log(`StudentParticipationResolver for Submission ${submissionId}.`);

        if (submissionId) {
            return this.textAssessmentsService.getFeedbackDataForExerciseSubmission(submissionId).catch(() => Observable.of(null));
        }
        return Observable.of(null);
    }
}

@Injectable({ providedIn: 'root' })
export class NewStudentParticipationResolver implements Resolve<StudentParticipation | null> {
    constructor(private textSubmissionService: TextSubmissionService, private location: Location) {}

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentsService.
     * @param route
     * @param state
     */
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const exerciseId = Number(route.paramMap.get('exerciseId'));
        console.log(`NewStudentParticipationResolver for Exercise ${exerciseId}.`);

        if (exerciseId) {
            this.textSubmissionService
                .getTextSubmissionForExerciseWithoutAssessment(exerciseId, true)
                .pipe(
                    tap((submission) => {
                        // Update the url with the new id, without reloading the page, to make the history consistent
                        const newUrl = window.location.hash.replace('#', '').replace('new', `${submission.id}`);
                        this.location.go(newUrl);
                    }),
                )
                .map((submission) => <StudentParticipation>submission.participation)
                .catch(() => Observable.of(null));
        }
        return Observable.of(null);
    }
}

export const textSubmissionAssessmentRoutes: Routes = [
    {
        path: 'new/assessment',
        component: TextSubmissionAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: NewStudentParticipationResolver,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':submissionId/assessment',
        component: TextSubmissionAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: StudentParticipationResolver,
        },
        canActivate: [UserRouteAccessService],
    },
];
