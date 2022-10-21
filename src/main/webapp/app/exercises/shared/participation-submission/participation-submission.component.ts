import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { Subject, Subscription, combineLatest, of } from 'rxjs';
import { catchError, map, take, tap } from 'rxjs/operators';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Submission } from 'app/entities/submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Result } from 'app/entities/result.model';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { HttpErrorResponse } from '@angular/common/http';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { EventManager } from 'app/core/util/event-manager.service';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
})
export class ParticipationSubmissionComponent implements OnInit {
    readonly ParticipationType = ParticipationType;
    readonly buttonSizeSmall = ButtonSize.SMALL;
    readonly actionTypeEmpty = ActionType.NoButtonTextDelete;

    // These two variables are used to emit errors to the delete dialog
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    @Input() participationId: number;

    public exerciseStatusBadge = 'bg-success';

    isTmpOrSolutionProgrParticipation = false;
    exercise?: Exercise;
    participation?: Participation;
    dueDate?: dayjs.Dayjs;
    submissions?: Submission[];
    eventSubscriber: Subscription;
    isLoading = true;
    commitHashURLTemplate?: string;

    // Icons
    faTimes = faTimes;

    constructor(
        private route: ActivatedRoute,
        private submissionService: SubmissionService,
        private translateService: TranslateService,
        private participationService: ParticipationService,
        private exerciseService: ExerciseService,
        private programmingExerciseService: ProgrammingExerciseService,
        private fileUploadAssessmentService: FileUploadAssessmentService,
        private modelingAssessmentsService: ModelingAssessmentService,
        private textAssessmentService: TextAssessmentService,
        private programmingAssessmentService: ProgrammingAssessmentManualResultService,
        private eventManager: EventManager,
        private translate: TranslateService,
        private profileService: ProfileService,
    ) {}

    /**
     * Initialize component by setting up page and subscribe to eventManager
     */
    ngOnInit() {
        this.setupPage();
        this.eventSubscriber = this.eventManager.subscribe('submissionsModification', () => this.setupPage());
    }

    /**
     * Set up page by loading participation and all submissions
     */
    setupPage() {
        this.isLoading = true;

        // If no query parameters are set, this.route.queryParams will be undefined so we need a fallback dummy observable
        combineLatest([this.route.params, this.route.queryParams ?? of(undefined)]).subscribe(([params, queryParams]) => {
            this.participationId = +params['participationId'];
            if (queryParams?.['isTmpOrSolutionProgrParticipation'] != undefined) {
                this.isTmpOrSolutionProgrParticipation = queryParams['isTmpOrSolutionProgrParticipation'] === 'true';
            }
            if (this.isTmpOrSolutionProgrParticipation) {
                // Find programming exercise of template and solution programming participation
                this.programmingExerciseService.findWithTemplateAndSolutionParticipation(params['exerciseId'], true).subscribe((exerciseResponse) => {
                    this.exercise = exerciseResponse.body!;
                    this.exerciseStatusBadge = dayjs().isAfter(dayjs(this.exercise.dueDate!)) ? 'bg-danger' : 'bg-success';
                    const templateParticipation = (this.exercise as ProgrammingExercise).templateParticipation;
                    const solutionParticipation = (this.exercise as ProgrammingExercise).solutionParticipation;

                    // Check if requested participationId belongs to the template or solution participation
                    if (this.participationId === templateParticipation?.id) {
                        this.participation = templateParticipation;
                        this.submissions = templateParticipation.submissions!;
                        // This is needed to access the exercise in the result details
                        templateParticipation.programmingExercise = this.exercise;
                    } else if (this.participationId === solutionParticipation?.id) {
                        this.participation = solutionParticipation;
                        this.submissions = solutionParticipation.submissions!;
                        // This is needed to access the exercise in the result details
                        solutionParticipation.programmingExercise = this.exercise;
                    } else {
                        // Should not happen
                        alert(this.translate.instant('artemisApp.participation.noParticipation'));
                    }
                    this.isLoading = false;
                });
            } else {
                // Get exercise for release and due dates
                this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                    this.exercise = exerciseResponse.body!;
                    this.updateStatusBadgeColor();
                });
                this.fetchParticipationAndSubmissionsForStudent();
            }
        });

        // Get active profiles, to distinguish between Bitbucket and GitLab
        this.profileService
            .getProfileInfo()
            .pipe(
                take(1),
                tap((info: ProfileInfo) => (this.commitHashURLTemplate = info?.commitHashURLTemplate)),
            )
            .subscribe();
    }

    fetchParticipationAndSubmissionsForStudent() {
        this.participationService
            .find(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of(null)),
            )
            .subscribe((participation) => {
                if (participation) {
                    this.participation = participation;
                    this.updateStatusBadgeColor();
                    this.isLoading = false;
                }
            });
        this.submissionService
            .findAllSubmissionsOfParticipation(this.participationId)
            .pipe(
                map(({ body }) => body),
                catchError(() => of([])),
            )
            .subscribe((submissions) => {
                if (submissions) {
                    this.submissions = submissions;
                    this.isLoading = false;
                    // set the submission to every result so it can be accessed via the result
                    submissions.forEach((submission: Submission) => {
                        if (submission.results) {
                            submission.results.forEach((result: Result) => (result.submission = submission));
                        }
                    });
                }
            });
    }

    getName() {
        if (this.participation?.type === ParticipationType.STUDENT || this.participation?.type === ParticipationType.PROGRAMMING) {
            return (this.participation as StudentParticipation).student?.name || (this.participation as StudentParticipation).team?.name;
        } else if (this.participation?.type === ParticipationType.SOLUTION) {
            return this.translate.instant('artemisApp.participation.solutionParticipation');
        } else if (this.participation?.type === ParticipationType.TEMPLATE) {
            return this.translate.instant('artemisApp.participation.templateParticipation');
        }
        return 'N/A';
    }

    getCommitUrl(submission: ProgrammingSubmission): string | undefined {
        return createCommitUrl(this.commitHashURLTemplate, (this.exercise as ProgrammingExercise)?.projectKey, this.participation, submission);
    }

    private updateStatusBadgeColor() {
        let afterDueDate = false;

        if (this.exercise && this.participation) {
            this.dueDate = getExerciseDueDate(this.exercise, this.participation);
            afterDueDate = hasExerciseDueDatePassed(this.exercise, this.participation);
        } else if (this.exercise) {
            afterDueDate = dayjs().isAfter(dayjs(this.exercise.dueDate!));
        }

        this.exerciseStatusBadge = afterDueDate ? 'bg-danger' : 'bg-success';
    }

    /**
     * Delete a submission from the server
     * @param submissionId - Id of submission that is deleted.
     */
    deleteSubmission(submissionId: number) {
        this.submissionService.delete(submissionId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'submissionsModification',
                    content: 'Deleted a submission',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    deleteResult(submission: Submission, result: Result) {
        if (this.exercise && submission.id && result.id && this.participationId) {
            switch (this.exercise.type) {
                case ExerciseType.TEXT:
                    this.textAssessmentService.deleteAssessment(this.participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.MODELING:
                    this.modelingAssessmentsService.deleteAssessment(this.participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.FILE_UPLOAD:
                    this.fileUploadAssessmentService.deleteAssessment(this.participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.PROGRAMMING:
                    this.programmingAssessmentService.deleteAssessment(this.participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
            }
        }
    }

    private updateResults(submission: Submission, result: Result) {
        submission.results = submission.results?.filter((remainingResult) => remainingResult.id !== result.id);
        this.dialogErrorSource.next('');
    }

    private handleErrorResponse(error: HttpErrorResponse) {
        if (error.error?.message === 'error.hasComplaint') {
            this.dialogErrorSource.next(this.translateService.instant('artemisApp.result.delete.error.hasComplaint'));
        } else {
            this.dialogErrorSource.next(error.message);
        }
    }
}
