import { AfterViewChecked, AfterViewInit, Component, EventEmitter, Input, OnDestroy, ViewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { PROFILE_AEOLUS, PROFILE_LOCALCI } from 'app/app.constants';
import { NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExerciseCustomAeolusBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-aeolus-build-plan.component';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { ProgrammingExerciseTheiaComponent } from 'app/exercises/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { DockerRunConfig } from 'app/entities/programming/programming-exercise-build.config';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseLanguageComponent implements AfterViewChecked, AfterViewInit, OnDestroy {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

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

    ngAfterViewInit() {
        this.fieldSubscriptions.push(this.selectLanguageField.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormValid())));
    }

    ngAfterViewChecked() {
        if (!(this.packageNameField?.valueChanges as EventEmitter<string>)?.observed) {
            this.fieldSubscriptions.push(this.packageNameField?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormValid())));
        }

        const dockerImageField =
            this.programmingExerciseCustomBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField ??
            this.programmingExerciseCustomAeolusBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField;
        if (!(dockerImageField?.valueChanges as EventEmitter<string>)?.observed) {
            this.fieldSubscriptions.push(dockerImageField?.valueChanges?.subscribe(() => this.calculateFormValid()));
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
            return this.programmingExerciseCustomBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField?.valid ?? false;
        }

        if (this.programmingExerciseCreationConfig.customBuildPlansSupported === PROFILE_AEOLUS) {
            return this.programmingExerciseCustomAeolusBuildPlanComponent?.programmingExerciseDockerImageComponent?.dockerImageField?.valid ?? false;
        }

        return true;
    }

    onDisableNetworkAccessChange(event: any) {
        let existingFlags = JSON.parse(this.programmingExercise.buildConfig?.dockerFlags || '{}') as DockerRunConfig;
        if (!(existingFlags && existingFlags.flags)) {
            existingFlags = new DockerRunConfig();
        }
        existingFlags.flags = existingFlags.flags?.filter((flag) => flag[0] !== DockerRunConfig.NETWORK_KEY) || [];
        if (event.target.checked) {
            existingFlags.flags.push([DockerRunConfig.NETWORK_KEY, 'none']);
        }
        this.programmingExercise.buildConfig!.dockerFlags = JSON.stringify(existingFlags.flags);
    }
}
