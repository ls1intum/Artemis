import { Component, OnInit, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { TableEditableFieldComponent } from 'app/shared/table/editable-field/table-editable-field.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

const NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES = [ProgrammingLanguage.EMPTY];

interface DockerFlags {
    network?: string;
    env?: { [key: string]: string };
    cpuCount?: number;
    memory?: number;
    memorySwap?: number;
}

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../../shared/programming-exercise-form.scss'],
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
    allowedCustomNetworks: string[];
    cpuCount: number | undefined;
    memory: number | undefined;
    memorySwap: number | undefined;
    dockerFlags: DockerFlags = {};

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');
    timeoutField = viewChild<NgModel>('timeoutField');

    network = signal<string | undefined>(undefined);

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
        // Note: we intentionally avoid auto-serializing docker flags here to prevent
        // writing incomplete flags before defaults are initialized in ngOnInit.
    }

    ngOnInit() {
        const profileInfo = this.profileService.getProfileInfo();
        if (profileInfo) {
            this.timeoutMinValue = profileInfo.buildTimeoutMin ?? 10;

            // Set the maximum timeout value to 240 if it is not set in the profile or if it is less than the minimum value
            this.timeoutMaxValue = profileInfo.buildTimeoutMax && profileInfo.buildTimeoutMax > this.timeoutMinValue ? profileInfo.buildTimeoutMax : 240;

            // Set the default timeout value to 120 if it is not set in the profile or if it is not in the valid range
            this.timeoutDefaultValue = 120;
            if (profileInfo.buildTimeoutDefault && profileInfo.buildTimeoutDefault >= this.timeoutMinValue && profileInfo.buildTimeoutDefault <= this.timeoutMaxValue) {
                this.timeoutDefaultValue = profileInfo.buildTimeoutDefault;
            }

            this.allowedCustomNetworks = profileInfo.allowedCustomDockerNetworks;

            if (!this.timeout) {
                this.timeoutChange.emit(this.timeoutDefaultValue);
            }

            if (!this.cpuCount) {
                this.cpuCount = profileInfo.defaultContainerCpuCount;
            }
            if (!this.memory) {
                this.memory = profileInfo.defaultContainerMemoryLimitInMB;
            }
            if (!this.memorySwap) {
                this.memorySwap = profileInfo.defaultContainerMemorySwapLimitInMB;
            }
        }

        if (this.programmingExercise()?.buildConfig?.dockerFlags) {
            this.initDockerFlags();
        }
    }

    initDockerFlags() {
        this.dockerFlags = JSON.parse(this.programmingExercise()?.buildConfig?.dockerFlags ?? '') as DockerFlags;
        if (this.dockerFlags.network) {
            this.network.set(this.dockerFlags.network);
        }
        if (this.dockerFlags.cpuCount) {
            this.cpuCount = this.dockerFlags.cpuCount;
        }
        if (this.dockerFlags.memory) {
            this.memory = this.dockerFlags.memory;
        }
        if (this.dockerFlags.memorySwap) {
            this.memorySwap = this.dockerFlags.memorySwap;
        }
        this.envVars = [];
        if (this.dockerFlags.env) {
            for (const key in this.dockerFlags.env) {
                this.envVars.push([key, this.dockerFlags.env?.[key] ?? '']);
            }
        }
    }

    onNetworkChange(value: any) {
        this.network.set(value);
        this.parseDockerFlagsToString();
    }

    onCpuCountChange(event: any) {
        this.cpuCount = event.target.value;
        this.parseDockerFlagsToString();
    }

    onMemoryChange(event: any) {
        this.memory = event.target.value;
        this.parseDockerFlagsToString();
    }

    onMemorySwapChange(event: any) {
        this.memorySwap = event.target.value;
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
        this.dockerFlags = { env: newEnv, network: this.network(), cpuCount: this.cpuCount, memory: this.memory, memorySwap: this.memorySwap };
        this.programmingExercise()!.buildConfig!.dockerFlags = JSON.stringify(this.dockerFlags);
    }

    setIsLanguageSupported() {
        this.isLanguageSupported = !NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY);
    }
}
