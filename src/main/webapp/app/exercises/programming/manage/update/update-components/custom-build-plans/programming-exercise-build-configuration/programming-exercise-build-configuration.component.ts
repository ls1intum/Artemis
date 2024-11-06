import { Component, OnInit, effect, inject, input, output, viewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';

const ALLOWED_DOCKER_FLAG_OPTIONS = ['network', 'env'];

const NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES = [ProgrammingLanguage.SWIFT, ProgrammingLanguage.HASKELL, ProgrammingLanguage.EMPTY];

const NETWORK_KEY: string = 'network';
const ENV_KEY: string = 'env';
export const ENV_VAR_REGEX = /(?:'([^']+)'|"([^"]+)"|(\w+))=(?:'([^']*)'|"([^"]*)"|([^,]+))/;

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    private profileService = inject(ProfileService);

    programmingExercise = input<ProgrammingExercise>();
    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    envVars: string = '';
    isNetworkDisabled = false;

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');
    timeoutField = viewChild<NgModel>('timeoutField');

    timeoutMinValue?: number;
    timeoutMaxValue?: number;
    timeoutDefaultValue?: number;

    isLanguageSupported: boolean = false;

    protected readonly envVarRegex = ENV_VAR_REGEX;

    constructor() {
        effect(() => {
            this.setIsLanguageSupported();
        });
    }

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.timeoutMinValue = profileInfo.buildTimeoutMin ?? 10;

                // Set the maximum timeout value to 240 if it is not set in the profile or if it is less than the minimum value
                this.timeoutMaxValue = profileInfo.buildTimeoutMax && profileInfo.buildTimeoutMax > this.timeoutMinValue ? profileInfo.buildTimeoutMax : 240;

                // Set the default timeout value to 120 if it is not set in the profile or if it is not in the valid range
                this.timeoutDefaultValue = 120;
                if (profileInfo.buildTimeoutDefault && profileInfo.buildTimeoutDefault >= this.timeoutMinValue && profileInfo.buildTimeoutDefault <= this.timeoutMaxValue) {
                    this.timeoutDefaultValue = profileInfo.buildTimeoutDefault;
                }

                if (!this.timeout) {
                    this.timeoutChange.emit(this.timeoutDefaultValue);
                }
            }
        });

        if (this.programmingExercise()?.buildConfig?.dockerFlags) {
            this.initDockerFlags();
        }
    }

    initDockerFlags() {
        const dockerFlags = JSON.parse(this.programmingExercise()?.buildConfig?.dockerFlags || '[]') as [string, string][];
        dockerFlags.forEach(([key, value]) => {
            if (key === NETWORK_KEY) {
                this.isNetworkDisabled = value === 'none';
            } else if (key === ENV_KEY) {
                this.envVars = value;
            }
        });
    }

    onDisableNetworkAccessChange(event: any) {
        this.isNetworkDisabled = event.target.checked;
        this.updateDockerFlags(NETWORK_KEY, event.target.checked ? 'none' : '');
    }

    onEnvVarsChange(event: any) {
        this.envVars = event;
        this.updateDockerFlags(ENV_KEY, event);
    }

    updateDockerFlags(key: string, value: string) {
        let existingFlags = JSON.parse(this.programmingExercise()?.buildConfig?.dockerFlags || '[]') as [string, string][];
        existingFlags = existingFlags.filter(([flag]) => ALLOWED_DOCKER_FLAG_OPTIONS.includes(flag) && flag !== key) || [];
        if (ALLOWED_DOCKER_FLAG_OPTIONS.includes(key) && value.trim() !== '') {
            existingFlags.push([key, value]);
        }
        this.programmingExercise()!.buildConfig!.dockerFlags = JSON.stringify(existingFlags);
    }

    setIsLanguageSupported() {
        this.isLanguageSupported = !NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY);
    }
}
