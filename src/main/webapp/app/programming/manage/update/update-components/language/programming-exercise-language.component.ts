import { AfterViewChecked, AfterViewInit, Component, EventEmitter, OnDestroy, input, viewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { FormsModule, NgModel } from '@angular/forms';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { ProgrammingExerciseTheiaComponent } from 'app/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { APP_NAME_PATTERN_FOR_SWIFT } from 'app/foundation/constants/input.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/foundation/pipes/remove-keys.pipe';

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
        KeyValuePipe,
        RemoveKeysPipe,
    ],
})
export class ProgrammingExerciseLanguageComponent implements AfterViewChecked, AfterViewInit, OnDestroy {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    readonly programmingExercise = input.required<ProgrammingExercise>();
    readonly programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    readonly selectLanguageField = viewChild<NgModel>('select');
    readonly packageNameField = viewChild<NgModel>('packageName');
    readonly programmingExerciseCustomBuildPlanComponent = viewChild(ProgrammingExerciseCustomBuildPlanComponent);
    readonly programmingExerciseTheiaComponent = viewChild(ProgrammingExerciseTheiaComponent);

    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    fieldSubscriptions: (Subscription | undefined)[] = [];

    faExclamationTriangle = faExclamationTriangle;
    protected readonly PROFILE_LOCALCI = PROFILE_LOCALCI;

    readonly DOCKER_REGISTRY_LINKS = {
        ghcrLink: 'https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry',
        dockerhubLink: 'https://hub.docker.com/',
    };
    readonly DOCUMENTATION_LINK = 'https://docs.artemis.tum.de/instructor/exercises/programming-exercise';

    protected readonly APP_NAME_PATTERN_FOR_SWIFT = APP_NAME_PATTERN_FOR_SWIFT;

    ngAfterViewInit() {
        this.fieldSubscriptions.push(this.selectLanguageField()?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormValid())));
    }

    ngAfterViewChecked() {
        const packageNameField = this.packageNameField();
        if (!(packageNameField?.valueChanges as EventEmitter<string>)?.observed) {
            this.fieldSubscriptions.push(packageNameField?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormValid())));
        }

        const dockerImageField = this.programmingExerciseCustomBuildPlanComponent()?.programmingExerciseDockerImageComponent()?.dockerImageField();
        if (!(dockerImageField?.valueChanges as EventEmitter<string>)?.observed) {
            this.fieldSubscriptions.push(dockerImageField?.valueChanges?.subscribe(() => this.calculateFormValid()));
        }

        const timeoutField = this.programmingExerciseCustomBuildPlanComponent()?.programmingExerciseDockerImageComponent()?.timeoutField();
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
        const selectLanguageField = this.selectLanguageField();
        const isPackageNameValid = this.isPackageNameValid();
        const isCustomBuildPlanValid = this.isCustomBuildPlanValid();
        this.formValid = Boolean((selectLanguageField?.isDisabled || selectLanguageField?.valid) && isPackageNameValid && isCustomBuildPlanValid);
        this.formValidChanges.next(this.formValid);
    }

    isPackageNameValid(): boolean {
        const packageNameField = this.packageNameField();
        return Boolean(
            !this.programmingExercise().programmingLanguage ||
            !this.programmingExerciseCreationConfig().packageNameRequired ||
            this.programmingExercise().projectType === ProjectType.XCODE ||
            packageNameField?.isDisabled ||
            packageNameField?.valid,
        );
    }

    isCustomBuildPlanValid(): boolean {
        if (!this.programmingExercise().customizeBuildPlan) {
            return true;
        }

        if (this.programmingExerciseCreationConfig().customBuildPlansSupported === PROFILE_LOCALCI) {
            return (
                (this.programmingExerciseCustomBuildPlanComponent()?.programmingExerciseDockerImageComponent()?.dockerImageField()?.valid ?? false) &&
                (this.programmingExerciseCustomBuildPlanComponent()?.programmingExerciseDockerImageComponent()?.timeoutField()?.valid ?? false)
            );
        }

        return true;
    }
}
