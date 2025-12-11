import { Component, OnChanges, OnInit, SimpleChanges, ViewChild, effect, inject, input, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
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
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ASSIGNMENT_REPO_NAME, TEST_REPO_NAME } from 'app/shared/constants/input.constants';
import { getDefaultContainerConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { AeolusService } from 'app/programming/shared/services/aeolus.service';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';

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
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit, OnChanges {
    private aeolusService = inject(AeolusService);
    private profileService = inject(ProfileService);

    programmingExercise = input<ProgrammingExercise>();
    programmingExerciseCreationConfig = input<ProgrammingExerciseCreationConfig>();
    containerConfigsList: DockerContainerConfig[];
    containerConfigsById: { [key: number]: DockerContainerConfig };
    dockerFlags: { [key: number]: DockerFlagsFlat };

    timeout = input<number>();

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

        this.onContainerConfigsChange(updatedConfigs);
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
                this.setTimeout(this.timeoutDefaultValue);
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

    // TODO: Everything from here comes from the file "programming-exercise-custom-build-plan.component.html" originally.
    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    staticCodeAnalysisEnabled?: boolean;
    sequentialTestRuns?: boolean;
    isImportFromFile = false;

    code: string = '#!/bin/bash\n\n# Add your custom build plan action here';
    private _editor?: MonacoEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: MonacoEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            if (this.programmingExercise()?.id || this.isImportFromFile) {
                this.code = getDefaultContainerConfig(this.programmingExercise()?.buildConfig).buildScript || '';
            }
            this._editor.setText(this.code);
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExerciseCreationConfig || changes.programmingExercise) {
            if (this.shouldReloadTemplate()) {
                const isImportFromFile = changes.programmingExerciseCreationConfig?.currentValue?.isImportFromFile ?? false;
                this.loadAeolusTemplate(isImportFromFile);
            }
        }
    }

    shouldReloadTemplate(): boolean {
        return (
            !this.programmingExercise()?.id &&
            (this.programmingExercise()?.programmingLanguage !== this.programmingLanguage ||
                this.programmingExercise()?.projectType !== this.projectType ||
                this.programmingExercise()?.staticCodeAnalysisEnabled !== this.staticCodeAnalysisEnabled ||
                this.programmingExercise()?.buildConfig!.sequentialTestRuns !== this.sequentialTestRuns)
        );
    }

    /**
     * In case the programming language or project type changes, we need to reset the template and the build plan
     * @private
     */
    resetCustomBuildPlan() {
        // TODO: this.programmingExercise.buildConfig!.windfile = undefined;
        getDefaultContainerConfig(this.programmingExercise()?.buildConfig!).buildPlanConfiguration = undefined;
        getDefaultContainerConfig(this.programmingExercise()?.buildConfig!).buildScript = undefined;
    }

    /**
     * Loads the predefined template for the selected programming language and project type
     * if there is one available.
     * @param isImportFromFile whether the exercise is imported from a file
     * @private
     */
    loadAeolusTemplate(isImportFromFile: boolean = false) {
        if (!this.programmingExercise()?.programmingLanguage) {
            return;
        }
        this.programmingLanguage = this.programmingExercise()?.programmingLanguage;
        this.projectType = this.programmingExercise()?.projectType;
        this.staticCodeAnalysisEnabled = this.programmingExercise()?.staticCodeAnalysisEnabled;
        this.sequentialTestRuns = this.programmingExercise()?.buildConfig?.sequentialTestRuns;
        this.isImportFromFile = isImportFromFile;
        // TODO: Revert to this: if (!isImportFromFile || !this.programmingExercise.buildConfig?.windfile) {
        if (true) {
            this.aeolusService.getAeolusTemplateFile(this.programmingLanguage!, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (file) => {
                    getDefaultContainerConfig(this.programmingExercise()?.buildConfig!).windfile = this.aeolusService.parseWindFile(file);
                },
                error: () => {
                    getDefaultContainerConfig(this.programmingExercise()?.buildConfig!).windfile = undefined;
                },
            });
        }

        this.programmingExerciseCreationConfig()!.buildPlanLoaded = true;
        /* TODO: if (!this.programmingExercise.buildConfig?.windfile) {
            this.resetCustomBuildPlan();
        }*/
        if (!isImportFromFile || !getDefaultContainerConfig(this.programmingExercise()?.buildConfig).buildScript) {
            this.aeolusService.getAeolusTemplateScript(this.programmingLanguage!, this.projectType, this.staticCodeAnalysisEnabled, this.sequentialTestRuns).subscribe({
                next: (file: string) => {
                    file = this.replacePlaceholders(file);
                    this.codeChanged(file);
                    this.editor?.setText(file);
                },
                error: () => {
                    getDefaultContainerConfig(this.programmingExercise()?.buildConfig!).buildScript = undefined;
                },
            });
        }
        if (!getDefaultContainerConfig(this.programmingExercise()?.buildConfig).buildScript) {
            this.resetCustomBuildPlan();
        }
        if (!this.programmingExercise()?.buildConfig?.timeoutSeconds) {
            this.programmingExercise()!.buildConfig!.timeoutSeconds = 0;
        }
    }

    get editor(): MonacoEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    // TODO: This has to be moved aswell, later!
    codeChanged(codeOrEvent: string | { text: string; fileName: string }): void {
        const code = typeof codeOrEvent === 'string' ? codeOrEvent : codeOrEvent.text;
        this.code = code;
        this.editor?.setText(code);
        getDefaultContainerConfig(this.programmingExercise()?.buildConfig!).buildScript = code;
    }

    /**
     * Sets up the Monaco editor for the build plan script
     */
    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        this._editor.changeModel('build-plan.sh', '');
    }

    onContainerConfigsChange(containerConfigs: { [key: string]: DockerContainerConfig }) {
        this.programmingExercise()!.buildConfig!.containerConfigs = containerConfigs;
        /* TODO: if (!this.programmingExercise.buildConfig?.windfile || !this.programmingExercise.buildConfig?.windfile.metadata.docker) {
            return;
        }*/
        // TODO: this.programmingExercise.buildConfig!.windfile.metadata.docker.image = dockerImage.trim();
    }

    setTimeout(timeout: number) {
        this.programmingExercise()!.buildConfig!.timeoutSeconds = timeout;
    }

    replacePlaceholders(buildScript: string): string {
        const assignmentRepoName = this.programmingExercise()?.buildConfig?.assignmentCheckoutPath || ASSIGNMENT_REPO_NAME;
        const testRepoName = this.programmingExercise()?.buildConfig?.testCheckoutPath || TEST_REPO_NAME;
        buildScript = buildScript.replaceAll('${studentParentWorkingDirectoryName}', assignmentRepoName);
        buildScript = buildScript.replaceAll('${testWorkingDirectory}', testRepoName);
        return buildScript;
    }
}
