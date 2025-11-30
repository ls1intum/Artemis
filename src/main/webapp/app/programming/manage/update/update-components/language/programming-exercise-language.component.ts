import { AfterViewChecked, AfterViewInit, Component, EventEmitter, Input, OnDestroy, ViewChild, input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { PROFILE_AEOLUS, PROFILE_LOCALCI } from 'app/app.constants';
import { FormsModule, NgModel } from '@angular/forms';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExerciseCustomAeolusBuildPlanComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-aeolus-build-plan.component';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { ProgrammingExerciseTheiaComponent } from 'app/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { APP_NAME_PATTERN_FOR_SWIFT } from 'app/shared/constants/input.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        ModePickerComponent,
        HelpIconComponent,
        FaIconComponent,
        ProgrammingExerciseTheiaComponent,
        ProgrammingExerciseCustomBuildPlanComponent,
        ProgrammingExerciseCustomAeolusBuildPlanComponent,
        KeyValuePipe,
        RemoveKeysPipe,
    ],
})
export class ProgrammingExerciseLanguageComponent implements AfterViewChecked, AfterViewInit, OnDestroy {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input({ required: true }) programmingExercise: ProgrammingExercise;
    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    @ViewChild('select') selectLanguageField: NgModel;
    @ViewChild('packageName') packageNameField?: NgModel;
    @ViewChild(ProgrammingExerciseCustomAeolusBuildPlanComponent) programmingExerciseCustomAeolusBuildPlanComponent?: ProgrammingExerciseCustomAeolusBuildPlanComponent;
    @ViewChild(ProgrammingExerciseCustomBuildPlanComponent) programmingExerciseCustomBuildPlanComponent?: ProgrammingExerciseCustomBuildPlanComponent;
    @ViewChild(ProgrammingExerciseTheiaComponent) programmingExerciseTheiaComponent?: ProgrammingExerciseTheiaComponent;

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    fieldSubscriptions: (Subscription | undefined)[] = [];

    faExclamationTriangle = faExclamationTriangle;
    protected readonly PROFILE_LOCALCI = PROFILE_LOCALCI;
    protected readonly PROFILE_AEOLUS = PROFILE_AEOLUS;

    readonly DOCKER_REGISTRY_LINKS = {
        ghcrLink: 'https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry',
        dockerhubLink: 'https://hub.docker.com/',
    };
    readonly DOCUMENTATION_LINK = 'https://docs.artemis.cit.tum.de/user/exercises/programming.html';

    protected readonly APP_NAME_PATTERN_FOR_SWIFT = APP_NAME_PATTERN_FOR_SWIFT;

    ngAfterViewInit() {
        this.fieldSubscriptions.push(this.selectLanguageField.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormValid())));
    }

    ngAfterViewChecked() {
        if (!(this.packageNameField?.valueChanges as EventEmitter<string>)?.observed) {
            this.fieldSubscriptions.push(this.packageNameField?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormValid())));
        }

        const dockerImageField =
            this.programmingExerciseCustomBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField() ??
            this.programmingExerciseCustomAeolusBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField();
        if (!(dockerImageField?.valueChanges as EventEmitter<string>)?.observed) {
            this.fieldSubscriptions.push(dockerImageField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        }

        const timeoutField =
            this.programmingExerciseCustomBuildPlanComponent?.programmingExerciseDockerImageComponent?.timeoutField() ??
            this.programmingExerciseCustomAeolusBuildPlanComponent?.programmingExerciseDockerImageComponent?.timeoutField();
        if (!(timeoutField?.valueChanges as EventEmitter<number>)?.observed) {
            this.fieldSubscriptions.push(timeoutField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        }
    }

    ngOnDestroy() {
        for (const subscription of this.fieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    calculateFormValid() {
        const isPackageNameValid = this.isPackageNameValid();
        const isCustomBuildPlanValid = this.isCustomBuildPlanValid();
        this.formValid = Boolean((this.selectLanguageField.isDisabled || this.selectLanguageField.valid) && isPackageNameValid && isCustomBuildPlanValid);
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

    isCustomBuildPlanValid(): boolean {
        if (!this.programmingExercise.customizeBuildPlanWithAeolus) {
            return true;
        }

        if (this.programmingExerciseCreationConfig.customBuildPlansSupported === PROFILE_LOCALCI) {
            return (
                (this.programmingExerciseCustomBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField()?.valid ?? false) &&
                (this.programmingExerciseCustomBuildPlanComponent?.programmingExerciseDockerImageComponent?.timeoutField()?.valid ?? false)
            );
        }

        if (this.programmingExerciseCreationConfig.customBuildPlansSupported === PROFILE_AEOLUS) {
            return (
                (this.programmingExerciseCustomAeolusBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField()?.valid ?? false) &&
                (this.programmingExerciseCustomAeolusBuildPlanComponent?.programmingExerciseDockerImageComponent?.timeoutField()?.valid ?? false)
            );
        }

        return true;
    }
}
