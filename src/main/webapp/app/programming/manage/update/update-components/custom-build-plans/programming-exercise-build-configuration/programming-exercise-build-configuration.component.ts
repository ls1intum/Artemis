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
import { getDefaultContainerConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

const NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES = [ProgrammingLanguage.EMPTY];

interface DockerFlags {
    network?: string;
    env?: { [key: string]: string };
    cpuCount?: number;
    memory?: number;
    memorySwap?: number;
}

interface MockDockerContainer {
    id: number;
    name: string;
    image: string;
    branch: string;
    script: string;
    timeoutSeconds: number;
    allowBranching: boolean;
    flags: string[];
    notes?: string;
    open?: boolean;
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
    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    envVars: [string, string][] = [];
    isNetworkDisabled = false;
    cpuCount: number | undefined;
    memory: number | undefined;
    memorySwap: number | undefined;
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
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;
    faPencil = faPencil;

    editingContainerId: number | null = null;

    mockDockerContainers: MockDockerContainer[] = [
        {
            id: 1,
            name: 'Default Container',
            image: 'artemis/default-runner:latest',
            branch: 'refs/heads/main',
            script: './gradlew clean test',
            timeoutSeconds: 420,
            allowBranching: false,
            flags: ['--cpus=2', '--memory=4g'],
            notes: 'Runs the standard Maven/Gradle tests',
            open: true,
        },
        {
            id: 2,
            name: 'GPU Evaluation',
            image: 'artemis/gpu-runner:cuda-12',
            branch: 'refs/heads/gpu-support',
            script: 'python evaluate.py',
            timeoutSeconds: 900,
            allowBranching: true,
            flags: ['--gpus=all', '--shm-size=2g'],
            notes: 'Used for ML grading tasks requiring CUDA',
            open: false,
        },
    ];

    constructor() {
        effect(() => {
            this.setIsLanguageSupported();
        });
    }

    toggleMockContainer(container: MockDockerContainer) {
        container.open = !container.open;
    }

    startEditing(container: MockDockerContainer) {
        this.editingContainerId = container.id;
    }

    stopEditing() {
        this.editingContainerId = null;
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

        if (getDefaultContainerConfig(this.programmingExercise()?.buildConfig).dockerFlags) {
            this.initDockerFlags();
        }
    }

    initDockerFlags() {
        const containerConfig = getDefaultContainerConfig(this.programmingExercise()?.buildConfig);
        this.dockerFlags = JSON.parse(containerConfig?.dockerFlags ?? '') as DockerFlags;

        this.isNetworkDisabled = this.dockerFlags.network === 'none';
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

    onDisableNetworkAccessChange(event: any) {
        this.isNetworkDisabled = event.target.checked;
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
        this.dockerFlags = { network: this.isNetworkDisabled ? 'none' : undefined, env: newEnv, cpuCount: this.cpuCount, memory: this.memory, memorySwap: this.memorySwap };
        getDefaultContainerConfig(this.programmingExercise()!.buildConfig!).dockerFlags = JSON.stringify(this.dockerFlags);
    }

    setIsLanguageSupported() {
        this.isLanguageSupported = !NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY);
    }
}
