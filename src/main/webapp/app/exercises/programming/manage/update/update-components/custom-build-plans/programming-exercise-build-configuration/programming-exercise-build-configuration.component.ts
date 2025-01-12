import { Component, OnInit, effect, inject, input, output, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

const NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES = [ProgrammingLanguage.SWIFT, ProgrammingLanguage.HASKELL, ProgrammingLanguage.EMPTY];

interface DockerFlags {
    network?: string;
    env?: { [key: string]: string };
}

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../programming-exercise-form.scss'],
    imports: [TranslateDirective, HelpIconComponent, FormsModule, NgxDatatableModule, TableEditableFieldComponent, FaIconComponent],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    private profileService = inject(ProfileService);

    programmingExercise = input<ProgrammingExercise>();
    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    envVars: [string, string][] = [];
    isNetworkDisabled = false;
    dockerFlags: DockerFlags = {};

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');
    timeoutField = viewChild<NgModel>('timeoutField');

    timeoutMinValue?: number;
    timeoutMaxValue?: number;
    timeoutDefaultValue?: number;

    isLanguageSupported = false;

    faPlus = faPlus;
    faTrash = faTrash;

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
        this.dockerFlags = JSON.parse(this.programmingExercise()?.buildConfig?.dockerFlags ?? '') as DockerFlags;
        this.isNetworkDisabled = this.dockerFlags.network === 'none';
        this.envVars = [];
        if (this.dockerFlags.env) {
            for (const key in this.dockerFlags.env) {
                this.envVars.push([key, this.dockerFlags.env?.[key] ?? '']);
            }
        }
    }

    onDisableNetworkAccessChange(event: any) {
        this.isNetworkDisabled = event.target.checked;
        this.parseDockerFlagsToString();
    }

    onEnvVarsKeyChange(row: [string, string]) {
        return (newValue: string) => {
            row[0] = newValue;
            this.parseDockerFlagsToString();
            return row[0];
        };
    }

    onEnvVarsValueChange(row: [string, string]) {
        return (newValue: string) => {
            row[1] = newValue;
            this.parseDockerFlagsToString();
            return row[1];
        };
    }

    addEnvVar() {
        this.envVars.push(['', '']);
    }

    removeEnvVar(index: number) {
        this.envVars.splice(index, 1);
        this.parseDockerFlagsToString();
    }

    parseDockerFlagsToString() {
        const newEnv = {} as { [key: string]: string } | undefined;
        this.envVars.forEach(([key, value]) => {
            if (key.trim()) {
                newEnv![key] = value;
            }
        });
        this.dockerFlags = { network: this.isNetworkDisabled ? 'none' : undefined, env: newEnv };
        this.programmingExercise()!.buildConfig!.dockerFlags = JSON.stringify(this.dockerFlags);
    }

    setIsLanguageSupported() {
        this.isLanguageSupported = !NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY);
    }
}
