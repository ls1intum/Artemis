import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { map } from 'rxjs/operators';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { AccountService } from 'app/core/auth/account.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { getLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    templateUrl: './file-upload-assessment-dashboard.component.html',
})
export class FileUploadAssessmentDashboardComponent implements OnInit {
    exercise: FileUploadExercise;
    submissions: FileUploadSubmission[] = [];
    filteredSubmissions: FileUploadSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;

    private cancelConfirmationText: string;

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private exerciseService: ExerciseService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploadAssessmentsService: FileUploadAssessmentsService,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * Get Submissions for exercise id.
     * If No Submissions are found, also get exercise. Otherwise, we get it from the first participation.
     */
    public async ngOnInit(): Promise<void> {
        console.log('in file-uplaod-assessmentdashboard');
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));

        await this.getSubmissions(exerciseId)
            // At least one submission present. Can get exercise from first submission.
            .then(() => {
                const exercise = this.submissions[0].participation!.exercise!;
                FileUploadAssessmentDashboardComponent.verifyFileUploadExercise(exercise);
                this.exercise = exercise as FileUploadExercise;
            })
            // No Submissions found. Need extra call to get exercise.
            .catch(async () => await this.getExercise(exerciseId));

        this.setPermissions();
        this.busy = false;
    }

    /**
     * Fetch submissions for Exercise id.
     * @param exerciseId
     * @return Resolved Promise if Submission list contains at least one submission. Rejected Promise if Submission list is empty.
     * @throws Error if exercise id is of other type.
     */
    private getSubmissions(exerciseId: number): Promise<void> {
        return new Promise((resolve, reject) => {
            this.fileUploadSubmissionService
                .getFileUploadSubmissionsForExerciseByCorrectionRound(exerciseId, { submittedOnly: true })
                .pipe(
                    map((response: HttpResponse<FileUploadSubmission[]>) =>
                        response.body!.map((submission: FileUploadSubmission) => {
                            const result = getLatestSubmissionResult(submission);
                            if (result) {
                                // reconnect some associations
                                result!.submission = submission;
                                result!.participation = submission.participation;
                                submission.participation!.results = [result!];
                            }
                            return submission;
                        }),
                    ),
                )
                .subscribe((submissions: FileUploadSubmission[]) => {
                    this.submissions = submissions;
                    this.filteredSubmissions = submissions;
                    if (submissions.length > 0) {
                        resolve();
                    } else {
                        reject();
                    }
                });
        });
    }

    updateFilteredSubmissions(filteredSubmissions: Submission[]) {
        this.filteredSubmissions = filteredSubmissions as FileUploadSubmission[];
    }

    /**
     * Fetch Exercise by id.
     * @param exerciseId
     * @return Resolve Promise once call is complete.
     * @throws Error if exercise id is of other type.
     */
    private getExercise(exerciseId: number): Promise<void> {
        return new Promise((resolve) => {
            this.exerciseService
                .find(exerciseId)
                .pipe(
                    map((exerciseResponse) => {
                        const exercise = exerciseResponse.body!;
                        FileUploadAssessmentDashboardComponent.verifyFileUploadExercise(exercise);
                        return <FileUploadExercise>exercise;
                    }),
                )
                .subscribe((exercise: FileUploadExercise) => {
                    this.exercise = exercise;
                    resolve();
                });
        });
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.fileUploadAssessmentsService.cancelAssessment(submission.id!).subscribe(() => {
                this.getSubmissions(this.exercise.id!);
            });
        }
    }

    private static verifyFileUploadExercise(exercise: Exercise): void {
        if (exercise.type !== ExerciseType.FILE_UPLOAD) {
            throw new Error('Cannot use File Upload Assessment Dashboard with different Exercise type.');
        }
    }

    private setPermissions() {
        if (this.exercise.course) {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course!);
        } else {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.exerciseGroup?.exam?.course!);
        }
    }

    public sortRows() {
        this.sortService.sortByProperty(this.submissions, this.predicate, this.reverse);
    }
}
