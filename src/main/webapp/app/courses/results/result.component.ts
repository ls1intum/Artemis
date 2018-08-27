import { Component, Input, OnChanges, OnDestroy, OnInit, Pipe, PipeTransform, SimpleChanges } from '@angular/core';
import { Participation, ParticipationService } from '../../entities/participation';
import { ParticipationResultService, Result, ResultService } from '../../entities/result';
import { JhiWebsocketService, Principal } from '../../shared';
import { RepositoryService } from '../../entities/repository/repository.service';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DomSanitizer } from '@angular/platform-browser';
import { HttpClient } from '@angular/common/http';
import { Feedback } from '../../entities/feedback';
import { ExerciseType } from '../../entities/exercise';

@Pipe({name: 'safeHtml'})
export class SafeHtmlPipe implements PipeTransform {
    constructor( private sanitizer: DomSanitizer ) { }
    transform(value) {
        return this.sanitizer.bypassSecurityTrustHtml(value);
    }
}

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

    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    @Input() participation: Participation;

    result: Result;
    websocketChannel: string;
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
            this.result = this.participation.results[0];

            this.init();
            // make sure result and participation are connected
            if (this.result) {
                this.result.participation = this.participation;
            }

            if (exercise && exercise.type === ExerciseType.PROGRAMMING) {
                this.principal.identity().then(account => { // only subscribe for the currently logged in user
                    const now = new Date();
                    if (account.id === this.participation.student.id && (exercise.dueDate == null ||
                        new Date(Date.parse(exercise.dueDate)) > now)) {

                        // subscribe for new results (e.g. when a programming exercise was automatically tested)
                        this.websocketChannel = `/topic/participation/${this.participation.id}/newResults`;
                        this.jhiWebsocketService.subscribe(this.websocketChannel);
                        this.jhiWebsocketService.receive(this.websocketChannel).subscribe(newResult => {
                            this.result = newResult;
                            this.init();
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
        }
        if (this.result.hasFeedback === null) {
            return false;
        }
        return this.result.hasFeedback;
    }

    showDetails(result: Result) {
        const modalRef = this.modalService.open(JhiResultDetailComponent, {keyboard: true, size: 'lg'});
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
export class JhiResultDetailComponent implements OnInit {
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
