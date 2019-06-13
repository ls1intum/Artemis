import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { orderBy as _orderBy } from 'lodash';
import { Participation, ParticipationService } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result, ResultService } from '.';
import { AccountService, JhiWebsocketService } from '../../core';
import { Subscription } from 'rxjs';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { HttpClient } from '@angular/common/http';
import { Exercise, ExerciseType } from 'app/entities/exercise';

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
    @Output() newResultReceived = new EventEmitter<boolean>();

    private resultUpdateListener: Subscription;

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
        if (!this.participation || !this.participation.id) {
            return;
        }

        if (this.result) {
            const exercise = this.participation.exercise;
            if (exercise && exercise.type === ExerciseType.PROGRAMMING) {
                this.subscribeForNewResults(exercise as ProgrammingExercise);
            }
        } else {
            const exercise = this.participation.exercise;

            // Sort participation results by completionDate desc.
            this.participation.results = this.participation.results && _orderBy(this.participation.results, 'completionDate', 'desc');
            // The active result is the first rated result in the sorted array (=newest) or any result if the option is active to show ungraded results.
            this.result = this.participation.results && this.participation.results.find(({ rated }) => this.showUngradedResults || rated === true);
            // Make sure that the participation result is connected to the newest result.
            this.result.participation = this.participation;

            this.subscribeForNewResults(exercise);
        }
    }

    subscribeForNewResults(exercise: Exercise) {
        this.accountService.identity().then(user => {
            // only subscribe for the currently logged in user or if the participation is a template/solution participation and the student is at least instructor
            const isInstructorInCourse = this.participation.student == null && exercise.course && this.accountService.isAtLeastInstructorInCourse(exercise.course);
            const isSameUser = this.participation.student && user.id === this.participation.student.id;
            const exerciseNotOver = exercise.dueDate == null || (moment(exercise.dueDate).isValid() && moment(exercise.dueDate).isAfter(moment()));

            if ((isSameUser && exerciseNotOver) || isInstructorInCourse) {
                this.resultUpdateListener = this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation.id).subscribe((newResult: Result) => {
                    if (newResult) {
                        newResult.completionDate = newResult.completionDate != null ? moment(newResult.completionDate) : null;
                        this.handleNewResult(newResult);
                    }
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
        this.newResultReceived.emit(true);
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
    }
}
