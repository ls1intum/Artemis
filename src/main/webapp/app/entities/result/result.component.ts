import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { Participation, ParticipationService } from '../participation';
import { Result, ResultDetailComponent, ResultService } from '.';
import { JhiWebsocketService, Principal } from '../../core';
import { RepositoryService } from '../repository/repository.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ExerciseType } from '../../entities/exercise';

import * as moment from 'moment';

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    providers: [ResultService, RepositoryService]
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges, OnDestroy {
    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    @Input()
    participation: Participation;
    @Input()
    isBuilding: boolean;
    @Output()
    newResult = new EventEmitter<object>();

    result: Result;
    websocketChannel: string;
    textColorClass: string;
    hasFeedback: boolean;
    resultIconClass: string;
    resultString: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private resultService: ResultService,
        private participationService: ParticipationService,
        private repositoryService: RepositoryService,
        private principal: Principal,
        private http: HttpClient,
        private modalService: NgbModal
    ) {}

    ngOnInit(): void {
        if (this.participation && this.participation.id) {
            const exercise = this.participation.exercise;

            if (this.participation.results && this.participation.results.length > 0) {
                // Make sure result and participation are connected
                this.result = this.participation.results[0];
                this.result.participation = this.participation;
            }

            this.init();

            if (exercise && exercise.type === ExerciseType.PROGRAMMING) {
                this.principal.identity().then(account => {
                    // only subscribe for the currently logged in user
                    if (account.id === this.participation.student.id && (exercise.dueDate == null || exercise.dueDate.isAfter(moment()))) {
                        // subscribe for new results (e.g. when a programming exercise was automatically tested)
                        this.websocketChannel = `/topic/participation/${this.participation.id}/newResults`;
                        this.jhiWebsocketService.subscribe(this.websocketChannel);
                        this.jhiWebsocketService.receive(this.websocketChannel).subscribe((newResult: Result) => {
                            // convert json string to moment
                            newResult.completionDate = newResult.completionDate != null ? moment(newResult.completionDate) : null;
                            this.handleNewResult(newResult);
                        });

                        // subscribe for new submissions (e.g. when code was pushed and is currently built)
                        this.websocketChannel = `/topic/participation/${this.participation.id}/newSubmission`;
                        this.jhiWebsocketService.subscribe(this.websocketChannel);
                        this.jhiWebsocketService.receive(this.websocketChannel).subscribe(newSubmission => {
                            // TODO handle this case properly, e.g. by animating a progress bar in the result view
                        });
                    }
                });
            }
        }
    }

    handleNewResult(newResult: Result) {
        this.result = newResult;
        // Reconnect the new result with the existing participation
        this.result.participation = this.participation;
        this.participation.results = [this.result];
        this.newResult.emit({
            newResult
        });
        this.init();
    }

    /*
     * fetch results from server, this method should only be invoked if there is no other possibility so that we avoid high server costs
     * TODO: in any case we should ask the server for the latest 'rated' result
     */
    refreshResult() {
        this.resultService
            .findResultsForParticipation(this.participation.exercise.course.id, this.participation.exercise.id, this.participation.id, {
                showAllResults: false,
                ratedOnly: this.participation.exercise.type === 'quiz'
            })
            .subscribe(results => {
                this.handleNewResult(results.body[0]);
            });
    }

    init() {
        if (this.result && (this.result.score || this.result.score === 0)) {
            this.textColorClass = this.getTextColorClass();
            this.hasFeedback = this.getHasFeedback();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.buildResultString();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation) {
            this.ngOnInit();
        }
    }

    ngOnDestroy() {
        if (this.websocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.websocketChannel);
        }
    }

    buildResultString() {
        if (this.result.resultString === 'No tests found') {
            return 'Build failed';
        }
        return this.result.resultString;
    }

    getHasFeedback() {
        if (this.result.resultString === 'No tests found') {
            return true;
        } else if (this.result.hasFeedback === null) {
            return false;
        }
        return this.result.hasFeedback;
    }

    showDetails(result: Result) {
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
        if (this.result.score == null) {
            if (this.result.successful) {
                return 'text-success';
            }
            return 'text-danger';
        }
        if (this.result.score > 80) {
            return 'text-success';
        }
        if (this.result.score > 40) {
            return 'result-orange';
        }
        return 'text-danger';
    }

    /**
     * Get the css class for the result icon as a string
     *
     * @return {string} the css class
     */
    getResultIconClass() {
        if (this.result.score == null) {
            if (this.result.successful) {
                return 'fa-check-circle-o';
            }
            return 'fa-times-circle-o';
        }
        if (this.result.score > 80) {
            return 'fa-check-circle-o';
        }
        return 'fa-times-circle-o';
    }
}
