import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ParticipationService, StudentParticipation, isModelingOrTextOrFileUpload } from 'app/entities/participation';
import { initializedResultWithScore } from 'app/entities/result/result-utils';
import { isSubmissionInDueTime } from 'app/entities/submission/submission-utils';
import { Result, ResultDetailComponent, ResultService } from '.';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ExerciseType } from 'app/entities/exercise';
import { MIN_POINTS_GREEN, MIN_POINTS_ORANGE } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core';
import * as moment from 'moment';

enum ResultTemplateStatus {
    IS_BUILDING = 'IS_BUILDING',
    HAS_RESULT = 'HAS_RESULT',
    NO_RESULT = 'NO_RESULT',
    SUBMITTED = 'SUBMITTED', // submitted, not yet graded
    LATE_NO_FEEDBACK = 'LATE_NO_FEEDBACK', // started, submitted too late, not graded
    LATE = 'LATE', // submitted too late, graded
}

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    providers: [ResultService, RepositoryService],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges {
    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly ResultTemplateStatus = ResultTemplateStatus;

    @Input() participation: StudentParticipation;
    @Input() isBuilding: boolean;
    @Input() short = false;
    @Input() result: Result | null;
    @Input() showUngradedResults: boolean;
    @Input() showGradedBadge = false;
    @Input() showTestNames = false;

    textColorClass: string;
    hasFeedback: boolean;
    resultIconClass: string[];
    resultString: string;
    templateStatus: ResultTemplateStatus;

    resultTooltip: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private resultService: ResultService,
        private participationService: ParticipationService,
        private repositoryService: RepositoryService,
        private translate: TranslateService,
        private http: HttpClient,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        if (!this.result && this.participation && this.participation.id) {
            const exercise = this.participation.exercise;

            if (this.participation.results && this.participation.results.length > 0) {
                if (exercise && exercise.type === ExerciseType.MODELING) {
                    // sort results by completionDate descending to ensure the newest result is shown
                    // this is important for modeling exercises since students can have multiple tries
                    // think about if this should be used for all types of exercises
                    this.participation.results.sort((r1: Result, r2: Result) => {
                        if (r1.completionDate! > r2.completionDate!) {
                            return -1;
                        }
                        if (r1.completionDate! < r2.completionDate!) {
                            return 1;
                        }
                        return 0;
                    });
                }
                // Make sure result and participation are connected
                this.result = this.participation.results[0];
                this.result.participation = this.participation;
            }
        }
        this.evaluate();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation || changes.result) {
            this.ngOnInit();
        }
    }

    evaluate() {
        this.evaluateTemplateStatus();

        if (this.templateStatus === ResultTemplateStatus.LATE) {
            this.textColorClass = this.getTextColorClass();
            this.resultIconClass = this.getResultIconClass();
        } else if (this.result && (this.result.score || this.result.score === 0) && (this.result.rated || this.result.rated == null || this.showUngradedResults)) {
            this.textColorClass = this.getTextColorClass();
            this.hasFeedback = this.getHasFeedback();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.buildResultString();
            this.resultTooltip = this.buildResultTooltip();
        } else {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            this.result = null;
        }
    }

    private evaluateTemplateStatus() {
        if (this.participation && this.participation.exercise && isModelingOrTextOrFileUpload(this.participation)) {
            // Evaluate the template status for modeling, text and file upload exercise.
            this.templateStatus = this.evaluateTemplateStatusForModelingTextFileUploadExercises();
        } else {
            // Evaluate the template status for quiz and programming exercise.
            this.templateStatus = this.evaluateTemplateStatusForQuizProgrammingExercises();
        }
    }

    /**
     * Evaluates the template status modeling, text and file upload exercises
     *
     * @return {ResultTemplateStatus}
     */
    private evaluateTemplateStatusForModelingTextFileUploadExercises() {
        const submissionInDueTime =
            !this.participation.exercise.dueDate ||
            (this.participation.submissions != null &&
                this.participation.submissions.length > 0 &&
                isSubmissionInDueTime(this.participation.submissions[0], this.participation.exercise));
        const assessmentDueDate = this.dateAsMoment(this.participation.exercise!.assessmentDueDate!);

        // Submission is in due time of exercise and has a result with score.
        if (submissionInDueTime && initializedResultWithScore(this.result)) {
            // Prevent that the result is shown before assessment due date
            return !assessmentDueDate || assessmentDueDate.isBefore() ? ResultTemplateStatus.HAS_RESULT : ResultTemplateStatus.NO_RESULT;
        } else if (submissionInDueTime && !initializedResultWithScore(this.result)) {
            // Submission is in due time of exercise and doesn't have a result with score.
            return ResultTemplateStatus.SUBMITTED;
        } else if (initializedResultWithScore(this.result) && (!assessmentDueDate || assessmentDueDate.isBefore())) {
            // Submission is not in due time of exercise, has a result with score and there is no assessmentDueDate for the exercise or it lies in the past.
            return ResultTemplateStatus.LATE;
        } else {
            // Submission is not in due time of exercise and tThere is actually no feedback for the submission or the feedback should not be displayed yet.
            return ResultTemplateStatus.LATE_NO_FEEDBACK;
        }
    }

    /**
     * Evaluates the template status quiz and programming exercises
     *
     * @return {ResultTemplateStatus}
     */
    private evaluateTemplateStatusForQuizProgrammingExercises() {
        if (this.isBuilding) {
            return ResultTemplateStatus.IS_BUILDING;
        } else if (initializedResultWithScore(this.result)) {
            return ResultTemplateStatus.HAS_RESULT;
        } else {
            return ResultTemplateStatus.NO_RESULT;
        }
    }

    private dateAsMoment(date: any) {
        if (date == null) {
            return null;
        }
        return moment.isMoment(date) ? date : moment(date);
    }

    buildResultString() {
        if (this.result!.resultString === 'No tests found') {
            return this.translate.instant('artemisApp.editor.buildFailed');
        }
        return this.result!.resultString;
    }

    buildResultTooltip() {
        if (this.result && this.result.resultString.includes('(preliminary)')) {
            return this.translate.instant('artemisApp.result.preliminary');
        }
    }

    getHasFeedback() {
        if (this.result!.resultString === 'No tests found') {
            return true;
        } else if (this.result!.hasFeedback === null) {
            return false;
        }
        return this.result!.hasFeedback;
    }

    private hasParticipationResults(): boolean {
        return this.participation && this.participation.results && this.participation.results.length > 0;
    }

    showDetails(result: Result) {
        if (!result.participation) {
            result.participation = this.participation;
        }
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = result;
        modalRef.componentInstance.showTestNames = this.showTestNames;
        modalRef.componentInstance.exerciseType = this.participation.exercise.type;
    }

    downloadBuildResult(participationId: number) {
        this.participationService.downloadArtifact(participationId).subscribe(artifact => {
            const fileURL = URL.createObjectURL(artifact);
            const a = document.createElement('a');
            a.href = fileURL;
            a.target = '_blank';
            a.download = 'artifact';
            document.body.appendChild(a);
            a.click();
        });
    }

    /**
     * Get the css class for the entire text as a string
     *
     * @return {string} the css class
     */
    getTextColorClass() {
        if (this.templateStatus === ResultTemplateStatus.LATE) {
            return 'result--late';
        }
        const result = this.result!;
        if (result.score == null) {
            if (result.successful) {
                return 'text-success';
            }
            return 'text-danger';
        }
        if (result.score > MIN_POINTS_GREEN) {
            return 'text-success';
        }
        if (result.score > MIN_POINTS_ORANGE) {
            return 'result-orange';
        }
        return 'text-danger';
    }

    /**
     * Get the icon type for the result icon as an array
     *
     */
    getResultIconClass(): string[] {
        const result = this.result!;
        if (result.score == null) {
            if (result.successful) {
                return ['far', 'check-circle'];
            }
            return ['far', 'times-circle'];
        }
        if (result.score > 80) {
            return ['far', 'check-circle'];
        }
        return ['far', 'times-circle'];
    }
}
