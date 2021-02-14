import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { map } from 'rxjs/operators';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseType } from 'app/entities/exercise.model';
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
    ExerciseType = ExerciseType;
    exercise: FileUploadExercise;
    submissions: FileUploadSubmission[] = [];
    filteredSubmissions: FileUploadSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;
    numberOfCorrectionrounds = 1;

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
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));

        this.exerciseService
            .find(exerciseId)
            .map((exerciseResponse) => {
                if (exerciseResponse.body!.type !== ExerciseType.FILE_UPLOAD) {
                    throw new Error('Cannot use Text Assessment Dashboard with non-text Exercise type.');
                }
                return <FileUploadExercise>exerciseResponse.body!;
            })
            .subscribe((exercise) => {
                this.exercise = exercise;
                this.getSubmissions();
                this.numberOfCorrectionrounds = this.exercise.exerciseGroup ? this.exercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
                this.setPermissions();
                this.busy = false;
            });
    }

    /**
     * Fetch submissions for Exercise id.
     * @param exerciseId
     * @return Resolved Promise if Submission list contains at least one submission. Rejected Promise if Submission list is empty.
     * @throws Error if exercise id is of other type.
     */
    private getSubmissions(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.fileUploadSubmissionService
                .getFileUploadSubmissionsForExerciseByCorrectionRound(this.exercise.id!, { submittedOnly: true })
                .pipe(
                    map((response: HttpResponse<FileUploadSubmission[]>) =>
                        response.body!.map((submission: FileUploadSubmission) => {
                            const tmpResult = getLatestSubmissionResult(submission);
                            if (tmpResult) {
                                // reconnect some associations
                                tmpResult.submission = submission;
                                tmpResult.participation = submission.participation;
                                submission.participation!.results = [tmpResult];
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
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.fileUploadAssessmentsService.cancelAssessment(submission.id!).subscribe(() => {
                this.getSubmissions();
            });
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
    /**
     * get the link for the assessment of a specific submission of the current exercise
     * @param submissionId
     */
    getAssessmentLink(submissionId: number) {
        if (this.exercise.exerciseGroup) {
            return ['/course-management', this.exercise.exerciseGroup.exam?.course?.id, 'file-upload-exercises', this.exercise.id, 'submissions', submissionId, 'assessment'];
        } else {
            return ['/course-management', this.exercise.course?.id, 'file-upload-exercises', this.exercise.id, 'submissions', submissionId, 'assessment'];
        }
    }
}
