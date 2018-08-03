import {Component, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges} from '@angular/core';
import {Participation, ParticipationService} from '../../entities/participation';
import { ParticipationResultService, Result, ResultService } from '../../entities/result';
import { JhiWebsocketService, Principal } from '../../shared';
import { RepositoryService } from '../../entities/repository/repository.service';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { Feedback } from '../../entities/feedback';

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    providers: [
        ParticipationResultService,
        RepositoryService
    ]
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges, OnDestroy {

    @Input() participation: Participation;
    @Input() building: boolean;
    @Output() newResult;

    results: Result[];
    result: Result;
    websocketChannel: string;
    // queued: boolean;
    textColorClass: string;
    hasFeedback: boolean;
    resultIconClass: string;
    resultString: string;

    constructor(private jhiWebsocketService: JhiWebsocketService,
                private participationResultService: ParticipationResultService,
                private participationService: ParticipationService,
                private repositoryService: RepositoryService,
                private principal: Principal,
                private http: HttpClient,
                private modalService: NgbModal) {}

    ngOnInit(): void {
        if (this.participation && this.participation.id) {
            const exercise = this.participation.exercise;
            this.results = this.participation.results;

            this.init();
            // make sure result and participation are connected
            if (this.result) {
                this.result.participation = this.participation;
            }

            if (exercise && exercise.type === 'programming-exercise') {
                this.principal.identity().then(account => { // only subscribe for the currently logged in user
                    const now = new Date();
                    if (account.id === this.participation.student.id && (exercise.dueDate == null ||
                        new Date(Date.parse(exercise.dueDate)) > now)) {

                        this.websocketChannel = `/topic/participation/${this.participation.id}/newResults`;
                        this.jhiWebsocketService.subscribe(this.websocketChannel);
                        this.jhiWebsocketService.receive(this.websocketChannel).subscribe(() => {
                            this.refresh(true);
                        });
                    }
                });
            }
        }
    }

    init() {
        if (this.results && this.results[0] && (this.results[0].score || this.results[0].score === 0)) {
            this.result = this.results[0];
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

    /**
     * refresh the participation and load the result if necessary
     *
     * @param forceLoad {boolean} force loading the result if the status is not QUEUED or BUILDING
     */
    refresh(forceLoad) {

        // TODO: Use WebSocket for participation status in place of GET 'api/participations/{vm.participationId}/status'

        // for now we just ignore participation status, as this is very costly for server performance
        this.refreshResult();

        /*this.http.get(`api/participations/${this.participation.id}/status`).finally(function(){
            if (!this.queued && !this.building) {
                if (this.participationResultService) {
                    this.participationResultService.query(
                        this.participation.exercises.course.id,
                        this.participation.exercise.id,
                        this.participation.id,
                        {showAllResults: false})
                        .subscribe((res: HttpResponse<Result[]>) => {
                                const results = res.body;
                                this.results = results;
                                //TODO handle this case
                                // if (results.onNewResults) {
                                //     results.onNewResult({ $event: {
                                //         newResult: results[0]
                                //     }});
                                // }
                            },
                            (res: HttpResponse<Result[]>) => this.onError(res.body)
                        );
                }
            }
        }).subscribe((response: string) => {
            this.queued = (response === 'QUEUED');
            this.building = (response === 'BUILDING');
        });*/
    }

    refreshResult() {
        console.log('Calling refreshResult() from result component');
        // TODO remove '!vm.participation.results' and think about removing forceLoad as well
        // load results from server
        // TODO: solve this differently?
        const that = this;
        this.participationResultService.query(this.participation.exercise.course.id, this.participation.exercise.id, this.participation.id, {
            showAllResults: false,
            ratedOnly: this.participation.exercise.type === 'quiz'
        }).subscribe(results => {
            this.results = results.body;
            console.log('received results', results);
            this.init();
            console.log('Calling init functin from result component, will emmit newResult now!');
            that.newResult.emit({
                newResult: results[0]
            });
        });
    }

    buildResultString() {
        if (this.result.resultString === 'No tests found') {
            return 'Build failed';
        }
        return this.result.resultString;
    }

    getHasFeedback() {
        if (this.results[0].resultString === 'No tests found') {
            return true;
        }
        if (this.results[0].hasFeedback === null) {
            return false;
        }
        return this.results[0].hasFeedback;
    }

    hasResults() {
        return !!this.results && this.results.length > 0 && this.result.score;
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(ResultDetailComponent, {keyboard: true, size: 'lg'});
        modalRef.componentInstance.result = result;
        // TODO: why is result.participation null?
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
            }
        );
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

// Modal -> Result details view
@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.html'
})
export class ResultDetailComponent implements OnInit {
    @Input() result: Result;
    loading: boolean;
    details: Feedback[];
    buildLogs;

    constructor(public activeModal: NgbActiveModal,
                private resultService: ResultService,
                private repositoryService: RepositoryService) {}

    ngOnInit(): void {
        this.loading = true;
        this.resultService.details(this.result.id).subscribe(res => {
            this.details = res.body;
            if (!this.details || this.details.length === 0) {
                this.repositoryService.buildlogs(this.result.participation.id).subscribe(repoResult => {
                   this.buildLogs = repoResult;
                   this.loading = false;
                });
            } else {
                this.loading = false;
            }
        });
        this.loading = false;
    }
}
