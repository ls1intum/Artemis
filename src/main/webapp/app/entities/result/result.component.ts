import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Participation, ParticipationService } from 'app/entities/participation';
import { Result, ResultDetailComponent, ResultService } from '.';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ExerciseType } from 'app/entities/exercise';
import { MIN_POINTS_GREEN, MIN_POINTS_ORANGE } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { AccountService, JhiWebsocketService } from 'app/core';

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

    @Input() participation: Participation;
    @Input() isBuilding: boolean;
    @Input() short = false;
    @Input() result: Result | null;
    @Input() showUngradedResults: boolean;
    @Input() showBadge = false;

    textColorClass: string;
    hasFeedback: boolean;
    resultIconClass: string[];
    resultString: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private resultService: ResultService,
        private participationService: ParticipationService,
        private repositoryService: RepositoryService,
        private accountService: AccountService,
        private translate: TranslateService,
        private http: HttpClient,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        if (this.result) {
            this.init();
        } else if (this.participation && this.participation.id) {
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

            this.init();
        }
    }

    init() {
        if (this.result && (this.result.score || this.result.score === 0) && (this.result.rated === true || this.result.rated == null || this.showUngradedResults)) {
            this.textColorClass = this.getTextColorClass();
            this.hasFeedback = this.getHasFeedback();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.buildResultString();
        } else {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            this.result = null;
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation || changes.result) {
            this.ngOnInit();
        }
    }

    buildResultString() {
        if (this.result!.resultString === 'No tests found') {
            return this.translate.instant('arTeMiSApp.editor.buildFailed');
        }
        return this.result!.resultString;
    }

    getHasFeedback() {
        if (this.result!.resultString === 'No tests found') {
            return true;
        } else if (this.result!.hasFeedback === null) {
            return false;
        }
        return this.result!.hasFeedback;
    }

    showDetails(result: Result) {
        if (!result.participation) {
            result.participation = this.participation;
        }
        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.result = result;
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
}
