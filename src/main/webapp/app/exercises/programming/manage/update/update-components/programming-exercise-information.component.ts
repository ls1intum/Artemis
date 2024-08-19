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
    protected readonly ProjectType = ProjectType;

    @Input() isImport: boolean = false;
    @Input() isExamMode: boolean = false;
    @Input({ required: true }) programmingExercise: ProgrammingExercise;
    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @Input() isLocal: boolean = false;

    @ViewChild(ExerciseTitleChannelNameComponent) exerciseTitleChannelComponent: ExerciseTitleChannelNameComponent;

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.exerciseTitleChannelComponent.titleChannelNameComponent?.formValidChanges.subscribe(() => this.calculateFormValid()));
    }

    ngOnDestroy(): void {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormValid() {
        this.formValid = this.exerciseTitleChannelComponent.titleChannelNameComponent?.formValid;
        this.formValidChanges.next(this.formValid);
    }
}
