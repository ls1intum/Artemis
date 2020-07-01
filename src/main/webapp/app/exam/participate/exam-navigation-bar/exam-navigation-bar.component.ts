import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { timer } from 'rxjs';
import { distinctUntilChanged, map, first } from 'rxjs/operators';

@Component({
    selector: 'jhi-exam-navigation-bar',
    templateUrl: './exam-navigation-bar.component.html',
    styleUrls: ['./exam-navigation-bar.component.scss'],
})
export class ExamNavigationBarComponent implements OnInit {
    @Input()
    exercises: Exercise[] = [];

    @Input()
    endDate: Moment;

    @Output() onExerciseChanged = new EventEmitter<{ exercise: Exercise; force: boolean }>();
    @Output() examAboutToEnd = new EventEmitter<void>();

    static itemsVisiblePerSideDefault = 4;

    exerciseIndex = 0;
    itemsVisiblePerSide = ExamNavigationBarComponent.itemsVisiblePerSideDefault;

    private timer$ = timer(0, 100).pipe(map(() => moment.duration(this.endDate.diff(this.serverDateService.now()))));

    displayTime$ = this.timer$.pipe(
        map((timeLeft: moment.Duration) => this.updateDisplayTime(timeLeft)),
        distinctUntilChanged(),
    );

    criticalTime = false;
    icon: string;

    constructor(private layoutService: LayoutService, private serverDateService: ArtemisServerDateService) {
        this.timer$.pipe(first((duration: moment.Duration) => duration.asSeconds() <= 1)).subscribe(() => this.examAboutToEnd.emit());
    }

    ngOnInit(): void {
        this.layoutService.subscribeToLayoutChanges().subscribe(() => {
            // You will have all matched breakpoints in observerResponse
            if (this.layoutService.isBreakpointActive(CustomBreakpointNames.extraLarge)) {
                this.itemsVisiblePerSide = ExamNavigationBarComponent.itemsVisiblePerSideDefault;
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.large)) {
                this.itemsVisiblePerSide = 3;
            } else if (this.layoutService.isBreakpointActive(CustomBreakpointNames.medium)) {
                this.itemsVisiblePerSide = 1;
            } else {
                this.itemsVisiblePerSide = 0;
            }
        });
    }

    changeExercise(exerciseIndex: number, force: boolean) {
        // out of index -> do nothing
        if (exerciseIndex > this.exercises.length - 1 || exerciseIndex < 0) {
            return;
        }
        // set index and emit event
        this.exerciseIndex = exerciseIndex;
        this.onExerciseChanged.emit({ exercise: this.exercises[exerciseIndex], force });
        this.setExerciseButtonStatus(exerciseIndex);
    }

    saveExercise() {
        const newIndex = this.exerciseIndex + 1;
        this.exercises[this.exerciseIndex].studentParticipations[0].submissions[0].submitted = true;
        if (newIndex > this.exercises.length - 1) {
            // we are in the last exercise, if out of range "change" active exercise to current in order to trigger a save
            this.changeExercise(this.exerciseIndex, true);
        } else {
            this.changeExercise(newIndex, true);
        }
    }

    updateDisplayTime(timeDiff: moment.Duration) {
        if (!this.criticalTime && timeDiff.asMinutes() < 5) {
            this.criticalTime = true;
        }
        return timeDiff.asMinutes() > 10
            ? Math.round(timeDiff.asMinutes()) + ' min'
            : timeDiff.minutes().toString().padStart(2, '0') + ' : ' + timeDiff.seconds().toString().padStart(2, '0') + ' min';
    }

    isProgrammingExercise() {
        return this.exercises[this.exerciseIndex].type === ExerciseType.PROGRAMMING;
    }

    setExerciseButtonStatus(i: number): string {
        let status: string;
        this.icon = 'edit';
        if (this.exercises[i].studentParticipations[0].submissions[0].submitted) {
            this.icon = 'check';
        }
        if (this.exercises[i].studentParticipations[0].submissions[0].isSynced) {
            // make button blue
            this.icon = 'check';
            status = 'synced';
            if (i === this.exerciseIndex) {
                status = status + ' active';
                return status;
            }
            return status;
        } else {
            // make button yellow
            status = 'notSynced';
            return status;
        }
    }
}
