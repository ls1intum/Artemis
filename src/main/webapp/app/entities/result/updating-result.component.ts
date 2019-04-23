import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { Participation, ParticipationService } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result, ResultDetailComponent, ResultService } from '.';
import { ProgrammingSubmission } from '../programming-submission';
import { AccountService, JhiWebsocketService } from '../../core';
import { Subscription } from 'rxjs';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ExerciseType } from 'app/entities/exercise';
import { MIN_POINTS_GREEN, MIN_POINTS_ORANGE } from 'app/app.constants';

import * as moment from 'moment';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-updating-result',
    templateUrl: './updating-result.component.html',
    providers: [ResultService, RepositoryService],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class UpdatingResultComponent implements OnInit, OnChanges, OnDestroy {
    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    @Input() participation: Participation;
    @Input() isBuilding: boolean;
    @Input() short = false;
    @Input() result: Result;
    @Input() showUngradedResults: boolean;

    private resultUpdateListener: Subscription;
    websocketChannelSubmissions: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private resultService: ResultService,
        private participationService: ParticipationService,
        private repositoryService: RepositoryService,
        private accountService: AccountService,
        private translate: TranslateService,
        private http: HttpClient,
        private participationWebsocketService: ParticipationWebsocketService,
    ) {}

    ngOnInit(): void {
        if (this.result) {
            const exercise = this.participation.exercise;
            if (exercise && exercise.type === ExerciseType.PROGRAMMING) {
                this.subscribeForProgramingExercise(exercise as ProgrammingExercise);
            }
        } else if (this.participation && this.participation.id) {
            const exercise = this.participation.exercise;

            if (this.participation.results && this.participation.results.length > 0) {
                if (exercise && exercise.type === ExerciseType.MODELING) {
                    // sort results by completionDate descending to ensure the newest result is shown
                    // this is important for modeling exercises since students can have multiple tries
                    // think about if this should be used for all types of exercises
                    this.participation.results.sort((r1: Result, r2: Result) => {
                        if (r1.completionDate > r2.completionDate) {
                            return -1;
                        }
                        if (r1.completionDate < r2.completionDate) {
                            return 1;
                        }
                        return 0;
                    });
                }
                // Make sure result and participation are connected
                this.result = this.participation.results[0];
                this.result.participation = this.participation;
            }

            if (exercise && exercise.type === ExerciseType.PROGRAMMING) {
                this.subscribeForProgramingExercise(exercise as ProgrammingExercise);
            }
        }
    }

    subscribeForProgramingExercise(exercise: ProgrammingExercise) {
        this.accountService.identity().then(user => {
            // only subscribe for the currently logged in user or if the participation is a template/solution participation and the student is at least instructor
            if (
                (this.participation.student && user.id === this.participation.student.id && (exercise.dueDate == null || exercise.dueDate.isAfter(moment()))) ||
                (this.participation.student == null && this.accountService.isAtLeastInstructorInCourse(exercise.course))
            ) {
                this.participationWebsocketService.addParticipation(this.participation);
                this.resultUpdateListener = this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation.id).subscribe((newResult: Result) => {
                    if (newResult) {
                        console.log('Received new result ' + newResult.id + ': ' + newResult.resultString);
                        newResult.completionDate = newResult.completionDate != null ? moment(newResult.completionDate) : null;
                        this.handleNewResult(newResult);
                    }
                });
                // unsubscribe old submissions if a subscription exists
                // subscribe for new submissions (e.g. when code was pushed and is currently built)
                if (this.websocketChannelSubmissions) {
                    this.jhiWebsocketService.unsubscribe(this.websocketChannelSubmissions);
                }
                this.websocketChannelSubmissions = `/topic/participation/${this.participation.id}/newSubmission`;
                this.jhiWebsocketService.subscribe(this.websocketChannelSubmissions);
                this.jhiWebsocketService.receive(this.websocketChannelSubmissions).subscribe((newProgrammingSubmission: ProgrammingSubmission) => {
                    // TODO handle this case properly, e.g. by animating a progress bar in the result view
                    console.log('Received new submission ' + newProgrammingSubmission.id + ': ' + newProgrammingSubmission.commitHash);
                });
            }
        });
    }

    handleNewResult(newResult: Result) {
        if (newResult.rated !== undefined && newResult.rated !== null && newResult.rated === false && !this.showUngradedResults) {
            // do not handle unrated results
            return;
        }
        this.result = newResult;
        // Reconnect the new result with the existing participation
        this.result.participation = this.participation;
        this.participation.results.push(this.result);
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation) {
            this.ngOnInit();
        }
    }

    ngOnDestroy() {
        if (this.resultUpdateListener) {
            this.resultUpdateListener.unsubscribe();
        }
        if (this.websocketChannelSubmissions) {
            this.jhiWebsocketService.unsubscribe(this.websocketChannelSubmissions);
        }
    }
}
