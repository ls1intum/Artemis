import { Component, OnInit, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TableEditableFieldComponent } from 'app/shared-ui/table/editable-field/table-editable-field.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { parseJson } from 'app/foundation/util/json.util';

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
    imports: [TranslateDirective, HelpIconComponent, FormsModule, TableEditableFieldComponent, FaIconComponent, TableViewComponent],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    private profileService = inject(ProfileService);

    programmingExercise = input<ProgrammingExercise>();
    dockerImage = input.required<string>();
    dockerImageChange = output<string>();

    timeout = input<number>();
    timeoutChange = output<number>();

    readonly envVars = signal<[string, string][]>([]);
    readonly allowedCustomNetworks = signal<string[] | undefined>(undefined);
    readonly cpuCount = signal<number | undefined>(undefined);
    readonly memory = signal<number | undefined>(undefined);
    readonly memorySwap = signal<number | undefined>(undefined);
    dockerFlags: DockerFlags = {};

    dockerImageField = viewChild<NgModel>('dockerImageField');
    timeoutField = viewChild<NgModel>('timeoutField');

    readonly envVarKeyTemplate = viewChild<CellTemplateRef<[string, string]>>('envVarKeyTemplate');
    readonly envVarValueTemplate = viewChild<CellTemplateRef<[string, string]>>('envVarValueTemplate');

    network = signal<string | undefined>(undefined);

    readonly timeoutMinValue = signal<number | undefined>(undefined);
    readonly timeoutMaxValue = signal<number | undefined>(undefined);
    readonly timeoutDefaultValue = signal<number | undefined>(undefined);

    readonly isLanguageSupported = signal(false);

    faPlus = faPlus;
    faTrash = faTrash;

    readonly envVarTableOptions: TableViewOptions = {
        lazy: false,
        paginated: false,
        showSearch: false,
        striped: true,
    };

    readonly envVarColumns = computed<ColumnDef<[string, string]>[]>(() => [
        {
            field: '0',
            header: 'Key',
            width: '200px',
            templateRef: this.envVarKeyTemplate(),
        },
        {
            field: '1',
            header: 'Value',
            templateRef: this.envVarValueTemplate(),
        },
    ]);

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
            const timeoutMinValue = profileInfo.buildTimeoutMin ?? 10;
            this.timeoutMinValue.set(timeoutMinValue);

            // Set the maximum timeout value to 240 if it is not set in the profile or if it is less than the minimum value
            const timeoutMaxValue = profileInfo.buildTimeoutMax && profileInfo.buildTimeoutMax > timeoutMinValue ? profileInfo.buildTimeoutMax : 240;
            this.timeoutMaxValue.set(timeoutMaxValue);

            // Set the default timeout value to 120 if it is not set in the profile or if it is not in the valid range
            let timeoutDefaultValue = 120;
            if (profileInfo.buildTimeoutDefault && profileInfo.buildTimeoutDefault >= timeoutMinValue && profileInfo.buildTimeoutDefault <= timeoutMaxValue) {
                timeoutDefaultValue = profileInfo.buildTimeoutDefault;
            }
            this.timeoutDefaultValue.set(timeoutDefaultValue);

            this.allowedCustomNetworks.set(profileInfo.allowedCustomDockerNetworks);

            if (!this.timeout()) {
                this.timeoutChange.emit(timeoutDefaultValue);
            }

            if (!this.cpuCount()) {
                this.cpuCount.set(profileInfo.defaultContainerCpuCount);
            }
            if (!this.memory()) {
                this.memory.set(profileInfo.defaultContainerMemoryLimitInMB);
            }
            if (!this.memorySwap()) {
                this.memorySwap.set(profileInfo.defaultContainerMemorySwapLimitInMB);
            }
        }

        if (this.programmingExercise()?.buildConfig?.dockerFlags) {
            this.initDockerFlags();
        }
    }

    initDockerFlags() {
        this.dockerFlags = parseJson<DockerFlags>(this.programmingExercise()?.buildConfig?.dockerFlags ?? '');
        if (this.dockerFlags.network) {
            this.network.set(this.dockerFlags.network);
        }
        if (this.dockerFlags.cpuCount) {
            this.cpuCount.set(this.dockerFlags.cpuCount);
        }
        if (this.dockerFlags.memory) {
            this.memory.set(this.dockerFlags.memory);
        }
        if (this.dockerFlags.memorySwap) {
            this.memorySwap.set(this.dockerFlags.memorySwap);
        }
        const envVars: [string, string][] = [];
        if (this.dockerFlags.env) {
            for (const key in this.dockerFlags.env) {
                envVars.push([key, this.dockerFlags.env?.[key] ?? '']);
            }
        }
        this.envVars.set(envVars);
    }

    onNetworkChange(value: string | undefined) {
        this.network.set(value);
        this.parseDockerFlagsToString();
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- input `$event` from the template and the numeric `{ target: { value } }` mock in the spec share no common non-any DOM type
    onCpuCountChange(event: any) {
        this.cpuCount.set(event.target.value);
        this.parseDockerFlagsToString();
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- input `$event` from the template and the numeric `{ target: { value } }` mock in the spec share no common non-any DOM type
    onMemoryChange(event: any) {
        this.memory.set(event.target.value);
        this.parseDockerFlagsToString();
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- input `$event` from the template and the numeric `{ target: { value } }` mock in the spec share no common non-any DOM type
    onMemorySwapChange(event: any) {
        this.memorySwap.set(event.target.value);
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
        this.envVars.update((envVars) => [...envVars, ['', '']]);
    }

    removeEnvVar(row: [string, string]) {
        this.envVars.update((envVars) => envVars.filter((envVar) => envVar !== row));
        this.parseDockerFlagsToString();
    }

    parseDockerFlagsToString() {
        const newEnv = {} as { [key: string]: string } | undefined;
        this.envVars().forEach(([key, value]) => {
            if (key.trim()) {
                newEnv![key] = value;
            }
        });
        const network = this.network() === '' ? undefined : this.network();
        this.dockerFlags = { env: newEnv, network: network, cpuCount: this.cpuCount(), memory: this.memory(), memorySwap: this.memorySwap() };
        this.programmingExercise()!.buildConfig!.dockerFlags = JSON.stringify(this.dockerFlags);
    }

    setIsLanguageSupported() {
        this.isLanguageSupported.set(!NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY));
    }
}
