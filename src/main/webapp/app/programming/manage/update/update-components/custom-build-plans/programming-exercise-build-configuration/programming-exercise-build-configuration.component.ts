import { Component, OnInit, effect, inject, input, output, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { faAngleDown, faAngleRight, faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { TableEditableFieldComponent } from 'app/shared/table/editable-field/table-editable-field.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { DockerContainerConfig } from 'app/programming/shared/entities/docker-container.config';
import { WindFile } from 'app/programming/shared/entities/wind.file';

const NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES = [ProgrammingLanguage.EMPTY];

interface DockerFlags {
    network?: string;
    env?: { [key: string]: string };
    cpuCount?: number;
    memory?: number;
    memorySwap?: number;
}

interface DockerFlagsFlat {
    envVars: [string, string][];
    isNetworkDisabled: boolean;
    cpuCount: number | undefined;
    memory: number | undefined;
    memorySwap: number | undefined;
    dockerFlags: DockerFlags;
    open: boolean;
}

@Component({
    selector: 'jhi-programming-exercise-build-configuration',
    templateUrl: './programming-exercise-build-configuration.component.html',
    styleUrls: ['../../../../../shared/programming-exercise-form.scss'],
    imports: [TranslateDirective, HelpIconComponent, FormsModule, NgxDatatableModule, TableEditableFieldComponent, FaIconComponent, MonacoEditorComponent],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    private profileService = inject(ProfileService);

    programmingExercise = input<ProgrammingExercise>();
    containerConfigsList: DockerContainerConfig[];
    containerConfigsById: { [key: number]: DockerContainerConfig };
    dockerFlags: { [key: number]: DockerFlagsFlat };

    containerConfigsChange = output<{ [key: string]: DockerContainerConfig }>();

    timeout = input<number>();
    timeoutChange = output<number>();

    isAeolus = input.required<boolean>();

    dockerImageField = viewChild<NgModel>('dockerImageField');
    timeoutField = viewChild<NgModel>('timeoutField');

    timeoutMinValue?: number;
    timeoutMaxValue?: number;
    timeoutDefaultValue?: number;

    isLanguageSupported = false;

    faPlus = faPlus;
    faTrash = faTrash;
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;
    faPencil = faPencil;

    editingContainerId: number | null = null;

    constructor() {
        effect(() => {
            this.setIsLanguageSupported();
        });
        effect(() => {
            this.containerConfigsList = Object.values(this.getContainerConfigs());
            this.containerConfigsById = {};
            this.dockerFlags = {};
            for (const containerConfig of this.containerConfigsList) {
                this.containerConfigsById[containerConfig.id] = containerConfig;
                this.dockerFlags[containerConfig.id] = this.initDockerFlags(containerConfig);
            }
        });
    }

    getContainerConfigs(): { [key: string]: DockerContainerConfig } {
        return this.programmingExercise()!.buildConfig!.containerConfigs;
    }

    toggleMockContainer(containerId: number) {
        const flags = this.dockerFlags[containerId];
        flags.open = !flags.open;
    }

    startEditing(containerId: number) {
        this.editingContainerId = containerId;
    }

    stopEditing() {
        this.editingContainerId = null;
    }

    onAddContainer() {
        const currentConfigs = this.getContainerConfigs();
        const nextIndex = Object.keys(currentConfigs).length + 1;
        const newName = `Container ${nextIndex}`;

        const newContainerConfig: DockerContainerConfig = {
            id: Date.now(),
            name: newName,
            buildPlanConfiguration: '',
            buildScript: '',
            dockerFlags: '',
            windfile: new WindFile(),
        };

        // TODO: Note for later this.defaultDockerFlags.cpuCount = profileInfo.defaultContainerCpuCount;
        // TODO: Note for later this.defaultDockerFlags.memory = profileInfo.defaultContainerMemoryLimitInMB;
        // TODO: Note for later this.defaultDockerFlags.memorySwap = profileInfo.defaultContainerMemorySwapLimitInMB;

        const updatedConfigs = {
            ...currentConfigs,
            [newName]: newContainerConfig,
        };
        // TODO: Name could be a duplicate!

        this.containerConfigsChange.emit(updatedConfigs);
        this.editingContainerId = newContainerConfig.id;

        // Update the local caches.
        this.containerConfigsById[newContainerConfig.id] = newContainerConfig;
        this.dockerFlags[newContainerConfig.id] = this.initDockerFlags(newContainerConfig);
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

            if (!this.timeout) {
                this.timeoutChange.emit(this.timeoutDefaultValue);
            }
        }
    }

    initDockerFlags(containerConfig: DockerContainerConfig): DockerFlagsFlat {
        const dockerFlags = JSON.parse(containerConfig?.dockerFlags ?? '') as DockerFlags;
        const dockerFlagsFlat: DockerFlagsFlat = {
            envVars: [],
            isNetworkDisabled: false,
            cpuCount: undefined,
            memory: undefined,
            memorySwap: undefined,
            dockerFlags: dockerFlags,
            open: false,
        };

        dockerFlagsFlat.isNetworkDisabled = dockerFlags.network === 'none';
        if (dockerFlags.cpuCount) {
            dockerFlagsFlat.cpuCount = dockerFlags.cpuCount;
        }
        if (dockerFlags.memory) {
            dockerFlagsFlat.memory = dockerFlags.memory;
        }
        if (dockerFlags.memorySwap) {
            dockerFlagsFlat.memorySwap = dockerFlags.memorySwap;
        }
        if (dockerFlags.env) {
            for (const key in dockerFlags.env) {
                dockerFlagsFlat.envVars.push([key, dockerFlags.env?.[key] ?? '']);
            }
        }

        return dockerFlagsFlat;
    }

    onDisableNetworkAccessChange(containerId: number, event: any) {
        this.dockerFlags[containerId].isNetworkDisabled = event.target.checked;
        this.parseDockerFlagsToString(containerId);
    }

    onCpuCountChange(containerId: number, event: any) {
        this.dockerFlags[containerId].cpuCount = event.target.value;
        this.parseDockerFlagsToString(containerId);
    }

    onMemoryChange(containerId: number, event: any) {
        this.dockerFlags[containerId].memory = event.target.value;
        this.parseDockerFlagsToString(containerId);
    }

    onMemorySwapChange(containerId: number, event: any) {
        this.dockerFlags[containerId].memorySwap = event.target.value;
        this.parseDockerFlagsToString(containerId);
    }

    onEnvVarsKeyChange(containerId: number, row: [string, string]) {
        return (newValue: string) => {
            row[0] = newValue;
            this.parseDockerFlagsToString(containerId);
            return row[0];
        };
    }

    onEnvVarsValueChange(containerId: number, row: [string, string]) {
        return (newValue: string) => {
            row[1] = newValue;
            this.parseDockerFlagsToString(containerId);
            return row[1];
        };
    }

    addEnvVar(containerId: number) {
        this.dockerFlags[containerId].envVars.push(['', '']);
    }

    removeEnvVar(containerId: number, index: number) {
        this.dockerFlags[containerId].envVars.splice(index, 1);
        this.parseDockerFlagsToString(containerId);
    }

    parseDockerFlagsToString(containerId: number) {
        const df = this.dockerFlags[containerId];
        const newEnv = {} as { [key: string]: string } | undefined;
        df.envVars.forEach(([key, value]) => {
            if (key.trim()) {
                newEnv![key] = value;
            }
        });
        df.dockerFlags = { network: df.isNetworkDisabled ? 'none' : undefined, env: newEnv, cpuCount: df.cpuCount, memory: df.memory, memorySwap: df.memorySwap };
        this.containerConfigsById[containerId].dockerFlags = JSON.stringify(df.dockerFlags);
        // TODO: I think this can go now. But keeping it in case i messed up! getDefaultContainerConfig(this.programmingExercise()!.buildConfig!).dockerFlags =
    }

    setIsLanguageSupported() {
        this.isLanguageSupported = !NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY);
    }
}
