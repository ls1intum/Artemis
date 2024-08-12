import { AfterViewInit, Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { Subject, Subscription } from 'rxjs';
import { ImportOptions } from 'app/types/programming-exercises';

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
    @Input() importOptions: ImportOptions;

    @ViewChild(ExerciseTitleChannelNameComponent) exerciseTitleChannelComponent: ExerciseTitleChannelNameComponent;
    @ViewChild('recreateBuildPlans') recreateBuildPlansField?: NgModel;
    @ViewChild('updateTemplateFiles') updateTemplateFilesField?: NgModel;

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    inputFieldSubscriptions: (Subscription | undefined)[] = [];

    protected readonly ProjectType = ProjectType;

    ngAfterViewInit() {
        this.inputFieldSubscriptions.push(this.exerciseTitleChannelComponent.titleChannelNameComponent?.formValidChanges.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.recreateBuildPlansField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.inputFieldSubscriptions.push(this.updateTemplateFilesField?.valueChanges?.subscribe(() => this.calculateFormValid()));
    }

    ngOnDestroy(): void {
        for (const subscription of this.inputFieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormValid() {
        const isRecreateBuildPlansValid = this.isRecreateBuildPlansValid();
        const isUpdateTemplateFilesValid = this.isUpdateTemplateFilesValid();
        this.formValid = Boolean(this.exerciseTitleChannelComponent.titleChannelNameComponent?.formValid && isRecreateBuildPlansValid && isUpdateTemplateFilesValid);
        this.formValidChanges.next(this.formValid);
    }

    isUpdateTemplateFilesValid(): boolean {
        return (
            this.updateTemplateFilesField?.valid ||
            !this.programmingExerciseCreationConfig.isImportFromExistingExercise ||
            this.programmingExercise.projectType === ProjectType.PLAIN_GRADLE ||
            this.programmingExercise.projectType === ProjectType.GRADLE_GRADLE
        );
    }

    isRecreateBuildPlansValid(): boolean {
        return this.recreateBuildPlansField?.valid || !this.programmingExerciseCreationConfig.isImportFromExistingExercise;
    }
}
