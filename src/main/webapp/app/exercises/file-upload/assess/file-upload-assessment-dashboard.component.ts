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
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { Submission, getLatestSubmissionResult } from 'app/entities/submission.model';
import { SortService } from 'app/shared/service/sort.service';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { faBan, faEdit, faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';
import { AbstractAssessmentDashboard } from 'app/exercises/shared/dashboards/tutor/abstract-assessment-dashboard';

@Component({
    templateUrl: './file-upload-assessment-dashboard.component.html',
})
export class FileUploadAssessmentDashboardComponent extends AbstractAssessmentDashboard implements OnInit {
    ExerciseType = ExerciseType;
    exercise: FileUploadExercise;
    submissions: FileUploadSubmission[] = [];
    filteredSubmissions: FileUploadSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;
    numberOfCorrectionrounds = 1;
    courseId: number;
    exerciseId: number;
    examId: number;
    exerciseGroupId: number;
    private cancelConfirmationText: string;

    // Icons
    faSort = faSort;
    faBan = faBan;
    faEdit = faEdit;
    faFolderOpen = faFolderOpen;

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private exerciseService: ExerciseService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploadAssessmentService: FileUploadAssessmentService,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {
        super();
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * Get submissions for exercise id.
     * If no submissions are found, also get exercise. Otherwise, we get it from the first participation.
     */
    ngOnInit() {
        this.busy = true;
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (this.route.snapshot.paramMap.has('examId')) {
            this.examId = Number(this.route.snapshot.paramMap.get('examId'));
            this.exerciseGroupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        }
        this.route.queryParams.subscribe((queryParams) => {
            if (queryParams['filterOption']) {
                this.filterOption = Number(queryParams['filterOption']);
            }
        });
        this.exerciseService
            .find(this.exerciseId)
            .pipe(
                map((exerciseResponse) => {
                    if (exerciseResponse.body!.type !== ExerciseType.FILE_UPLOAD) {
                        throw new Error('Cannot use File-Upload Assessment Dashboard with non-file-upload Exercise type.');
                    }
                    return <FileUploadExercise>exerciseResponse.body!;
                }),
            )
            .subscribe((exercise) => {
                this.exercise = exercise;
                this.getSubmissions();
                this.numberOfCorrectionrounds = this.exercise.exerciseGroup ? this.exercise!.exerciseGroup.exam!.numberOfCorrectionRoundsInExam! : 1;
                this.busy = false;
            });
    }

    /**
     * Fetch submissions for exercise id.
     */
    private getSubmissions() {
        this.fileUploadSubmissionService
            .getSubmissions(this.exercise.id!, { submittedOnly: true })
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
                this.applyChartFilter(submissions);
                this.busy = false;
            });
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.fileUploadAssessmentService.cancelAssessment(submission.id!).subscribe(() => {
                this.getSubmissions();
            });
        }
    }

    public sortRows() {
        this.sortService.sortByProperty(this.submissions, this.predicate, this.reverse);
    }

    /**
     * Get the link for the assessment of a specific submission of the current exercise
     * @param participationId
     * @param submissionId
     */
    getAssessmentLink(participationId: number, submissionId: number) {
        return getLinkToSubmissionAssessment(this.exercise.type!, this.courseId, this.exerciseId, participationId, submissionId, this.examId, this.exerciseGroupId);
    }
}
