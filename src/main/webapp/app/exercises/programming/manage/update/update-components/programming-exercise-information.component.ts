import { AfterViewInit, Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { Subject, Subscription } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../programming-exercise-form.scss', 'programming-exercise-information.component.scss'],
})
export class ProgrammingExerciseInformationComponent implements AfterViewInit, OnDestroy {
    @Input() isImport: boolean;
    @Input() isExamMode: boolean;
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @Input() isLocal: boolean;

    @ViewChild(ExerciseTitleChannelNameComponent) exerciseTitleChannelComponent: ExerciseTitleChannelNameComponent;

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    protected readonly ProjectType = ProjectType;

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.exerciseTitleChannelComponent.titleChannelNameComponent?.formValidChanges.subscribe(() => this.calculateFormValid()));
    }

    ngOnDestroy(): void {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormValid() {
        this.formValid = Boolean(this.exerciseTitleChannelComponent.titleChannelNameComponent?.formValid);
        this.formValidChanges.next(this.formValid);
    }
}
