import { AfterViewInit, Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { PROFILE_AEOLUS, PROFILE_LOCALCI } from 'app/app.constants';
import { NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseLanguageComponent implements AfterViewInit, OnDestroy {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    @ViewChild('select') selectLanguageField: NgModel;
    @ViewChild('packageName') packageNameField?: NgModel;

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    fieldSubscriptions: (Subscription | undefined)[] = [];

    faQuestionCircle = faQuestionCircle;
    protected readonly PROFILE_LOCALCI = PROFILE_LOCALCI;
    protected readonly PROFILE_AEOLUS = PROFILE_AEOLUS;

    ngAfterViewInit() {
        this.fieldSubscriptions.push(this.selectLanguageField.valueChanges?.subscribe(() => this.calculateFormValid()));
        this.fieldSubscriptions.push(this.packageNameField?.valueChanges?.subscribe(() => this.calculateFormValid()));
    }

    ngOnDestroy() {
        for (const subscription of this.fieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormValid() {
        const isPackageNameValid = this.isPackageNameValid();
        this.formValid = Boolean((this.selectLanguageField.isDisabled || this.selectLanguageField.valid) && isPackageNameValid);
        this.formValidChanges.next(this.formValid);
    }

    isPackageNameValid(): boolean {
        return Boolean(
            !this.programmingExercise.programmingLanguage ||
                !this.programmingExerciseCreationConfig.packageNameRequired ||
                this.programmingExercise.projectType === ProjectType.XCODE ||
                this.packageNameField?.isDisabled ||
                this.packageNameField?.valid,
        );
    }
}
