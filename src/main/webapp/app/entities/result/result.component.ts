import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ParticipationService, StudentParticipation, InitializationState, isModelingOrTextOrFileUpload } from 'app/entities/participation';
import { initializedResultWithScore } from 'app/entities/result';
import { Result, ResultDetailComponent, ResultService } from '.';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { Course } from 'app/entities/course';
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

    @Input() course: Course;
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
        // Get results initially if necessary
        if (this.participation && !this.hasParticipationResults() && this.course && this.participation.exercise) {
            this.resultService.findResultsForParticipation(this.course.id, this.participation.exercise.id, this.participation.id).subscribe(
                results => {
                    this.participation.results = results.body!;
                    this.init();
                },
                error => {
                    this.init();
                },
            );
        } else {
            this.init();
        }
    }

    init() {
        if (this.result) {
            this.evaluate();
        } else if (this.participation && this.participation.id) {
            if (this.hasParticipationResults()) {
                const result = this.getLatestResult(this.participation.results);

                // Make sure result and participation are connected
                this.result = result;
                this.result.participation = this.participation;
            }

            this.evaluate();
        }
    }

    evaluate() {
        this.evaluateTemplateStatus();

        if (this.result && (this.result.score || this.result.score === 0) && (this.result.rated || this.result.rated == null || this.showUngradedResults)) {
            this.textColorClass = this.getTextColorClass();
            this.hasFeedback = this.getHasFeedback();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.buildResultString();
            this.resultTooltip = this.buildResultTooltip();
        } else if (this.templateStatus === ResultTemplateStatus.LATE) {
            this.textColorClass = 'result-gray';
            this.resultIconClass = this.getResultIconClass();
        } else {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            this.result = null;
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation || changes.result || changes.participation) {
            this.init();
        }
    }

    evaluateTemplateStatus() {
        if (isModelingOrTextOrFileUpload(this.participation) && this.participation && this.participation.exercise) {
            const assessmentDueDate = this.dateAsMoment(this.participation.exercise!.assessmentDueDate!);
            if (this.isSubmissionInDueTime()) {
                if (initializedResultWithScore(this.result)) {
                    // Prevent that result is shown before assessment due date
                    if (!assessmentDueDate || assessmentDueDate.isBefore()) {
                        this.templateStatus = ResultTemplateStatus.HAS_RESULT;
                    } else {
                        this.templateStatus = ResultTemplateStatus.NO_RESULT;
                    }
                } else {
                    this.templateStatus = ResultTemplateStatus.SUBMITTED;
                }
            } else if (initializedResultWithScore(this.result) && (!assessmentDueDate || assessmentDueDate.isBefore())) {
                this.templateStatus = ResultTemplateStatus.LATE;
            } else {
                this.templateStatus = ResultTemplateStatus.LATE_NO_FEEDBACK;
            }
        } else {
            if (this.isBuilding) {
                this.templateStatus = ResultTemplateStatus.IS_BUILDING;
            } else if (initializedResultWithScore(this.result)) {
                this.templateStatus = ResultTemplateStatus.HAS_RESULT;
            } else {
                this.templateStatus = ResultTemplateStatus.NO_RESULT;
            }
        }
    }

    dateAsMoment(date: any) {
        if (date) {
            if (moment.isMoment(date)) {
                return date;
            } else {
                return moment(date);
            }
        }
        return null;
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

    hasParticipationResults(): boolean {
        if (!this.participation) {
            return false;
        }
        return this.participation.results != null && this.participation.results.length > 0;
    }

    isSubmissionInDueTime(): boolean {
        const submission = this.participation.submissions[0];
        if (submission && submission.submissionDate && this.participation.exercise.dueDate) {
            submission.submissionDate = moment(submission.submissionDate);
            return submission.submissionDate.isBefore(this.participation.exercise.dueDate);
        } else {
            return !this.participation.exercise.dueDate;
        }
    }

    showDetails(result: Result) {
        if (!result.participation) {
            result.participation = this.participation;
        }
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = result;
        modalRef.componentInstance.showTestNames = this.showTestNames;
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

    /**
     * Find latest result in results array
     *
     * @param {Result} results
     */
    getLatestResult(results: Result[]) {
        results.sort((r1: Result, r2: Result) => {
            if (r1.completionDate! > r2.completionDate!) {
                return -1;
            }
            if (r1.completionDate! < r2.completionDate!) {
                return 1;
            }
            return 0;
        });

        return results[0];
    }
}
