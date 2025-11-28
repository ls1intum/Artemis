import { Component, Input, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { Subject, Subscription, combineLatest, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { HttpErrorResponse } from '@angular/common/http';
import { EventManager } from 'app/shared/service/event-manager.service';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ResultComponent } from '../result/result.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { isStudentParticipation } from 'app/exercise/result/result.utils';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
    imports: [TranslateDirective, NgClass, NgxDatatableModule, ResultComponent, DeleteButtonDirective, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe, ArtemisTimeAgoPipe],
})
export class ParticipationSubmissionComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private submissionService = inject(SubmissionService);
    private translateService = inject(TranslateService);
    private participationService = inject(ParticipationService);
    private exerciseService = inject(ExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private modelingAssessmentsService = inject(ModelingAssessmentService);
    private textAssessmentService = inject(TextAssessmentService);
    private programmingAssessmentService = inject(ProgrammingAssessmentManualResultService);
    private eventManager = inject(EventManager);

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
    resultIdToBuildJobIdMap?: { [key: string]: string };

    // Icons
    faTrash = faTrash;

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
            this.participationService.getBuildJobIdsForResultsOfParticipation(this.participationId).subscribe((resultIdToBuildJobIdMap) => {
                this.resultIdToBuildJobIdMap = resultIdToBuildJobIdMap;
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
                            alert(this.translateService.instant('artemisApp.participation.noParticipation'));
                        }

                        if (this.submissions) {
                            this.submissions.forEach((submission: ProgrammingSubmission) => {
                                if (submission.results) {
                                    submission.results.forEach((result: Result) => {
                                        result.buildJobId = this.resultIdToBuildJobIdMap?.[result.id!];
                                    });
                                }
                            });
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
                    this.isLoading = false;
                }
            });
        });
    }

    fetchParticipationAndSubmissionsForStudent() {
        combineLatest([this.participationService.find(this.participationId), this.submissionService.findAllSubmissionsOfParticipation(this.participationId)])
            .pipe(
                map((res) => [res[0].body, res[1].body] as [Participation | undefined, Submission[] | undefined]),
                catchError(() => of(null)),
            )
            .subscribe((response: [Participation | undefined, Submission[] | undefined] | null) => {
                this.isLoading = false;
                if (!response) {
                    return;
                }

                const participation = response[0];
                const submissions = response[1];
                if (isStudentParticipation(participation)) {
                    this.participation = participation;
                    this.updateStatusBadgeColor();
                }

                if (submissions) {
                    this.submissions = submissions;
                    if (this.participation) {
                        this.participation.submissions = submissions;
                    }
                    // set the submission to every result so it can be accessed via the result
                    // set the build log availability for every result
                    submissions.forEach((submission: Submission) => {
                        if (submission.results) {
                            submission.results.forEach((result: Result) => {
                                result.submission = submission;
                                result.buildJobId = this.resultIdToBuildJobIdMap?.[result.id!];
                            });
                        }
                    });
                }
            });
    }

    getName() {
        if (isStudentParticipation(this.participation)) {
            return this.participation.student?.name || this.participation.team?.name;
        } else if (this.participation?.type === ParticipationType.SOLUTION) {
            return this.translateService.instant('artemisApp.participation.solutionParticipation');
        } else if (this.participation?.type === ParticipationType.TEMPLATE) {
            return this.translateService.instant('artemisApp.participation.templateParticipation');
        }
        return 'N/A';
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

    viewBuildLogs(resultId: number): void {
        const url = `/api/programming/build-log/${resultId}`;
        window.open(url, '_blank');
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
