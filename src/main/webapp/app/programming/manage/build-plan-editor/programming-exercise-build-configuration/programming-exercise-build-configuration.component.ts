import { Component, OnInit, WritableSignal, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TableEditableFieldComponent } from 'app/shared-ui/table/editable-field/table-editable-field.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { parseJson } from 'app/foundation/util/json.util';
import { DOCKER_FLAGS_MAX_LENGTH } from 'app/programming/shared/entities/programming-exercise-build.config';

const NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES = [ProgrammingLanguage.EMPTY];

// the bounds the server applies in ProgrammingExerciseValidationService#validateDockerFlags, mirrored here so an invalid
// value is caught inline instead of only by the save request
const MIN_DOCKER_CPU_COUNT = 1;
const MIN_DOCKER_MEMORY_MB = 6;
const MIN_DOCKER_MEMORY_SWAP_MB = 0;

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
    styleUrls: ['../../../shared/programming-exercise-form.scss'],
    imports: [TranslateDirective, HelpIconComponent, FormsModule, TableEditableFieldComponent, FaIconComponent, TableViewComponent],
})
export class ProgrammingExerciseBuildConfigurationComponent implements OnInit {
    private profileService = inject(ProfileService);

    programmingExercise = input<ProgrammingExercise>();

    timeout = input<number>();
    timeoutChange = output<number>();

    readonly envVars = signal<[string, string][]>([]);
    readonly allowedCustomNetworks = signal<string[] | undefined>(undefined);
    readonly cpuCount = signal<number | undefined>(undefined);
    readonly memory = signal<number | undefined>(undefined);
    readonly memorySwap = signal<number | undefined>(undefined);
    dockerFlags: DockerFlags = {};

    readonly envVarKeyTemplate = viewChild<CellTemplateRef<[string, string]>>('envVarKeyTemplate');
    readonly envVarValueTemplate = viewChild<CellTemplateRef<[string, string]>>('envVarValueTemplate');

    network = signal<string | undefined>(undefined);

    readonly timeoutMinValue = signal<number | undefined>(undefined);
    readonly timeoutMaxValue = signal<number | undefined>(undefined);
    readonly timeoutDefaultValue = signal<number | undefined>(undefined);

    // a stored timeout of 0 means "use the global default", which the slider (bounded by the profile minimum) cannot
    // represent, so render it at the default position while leaving the bound value at 0 until the instructor drags it
    readonly usesDefaultTimeout = computed(() => !this.timeout());
    readonly displayTimeout = computed(() => (this.usesDefaultTimeout() ? (this.timeoutDefaultValue() ?? 0) : this.timeout()!));

    readonly isLanguageSupported = signal(false);

    readonly isCpuCountValid = signal(true);
    readonly isMemoryValid = signal(true);
    readonly isMemorySwapValid = signal(true);

    // the editor page blocks saving while a resource limit is invalid, so the server never has to reject the payload
    readonly areDockerResourcesValid = computed(() => this.isCpuCountValid() && this.isMemoryValid() && this.isMemorySwapValid());

    // The serialized flags as last written by parseDockerFlagsToString.
    private readonly serializedDockerFlags = signal<string>('{}');

    readonly areDockerFlagsWithinSizeLimit = computed(() => this.serializedDockerFlags().length <= DOCKER_FLAGS_MAX_LENGTH);

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

            // intentionally do not emit the default timeout into the model: a stored 0 means "use the global default", and
            // writing 120 here would pin that value on the next save so the exercise stops following default changes

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
        this.serializedDockerFlags.set(this.programmingExercise()?.buildConfig?.dockerFlags ?? '{}');
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

    /**
     * Applies a Docker resource limit entered as text. The fields are free text, so the value is first parsed into the
     * whole number the server expects; without that a value such as "abc" would be packaged into the Docker flags verbatim
     * and the save would fail with a dockerFlagsParsingError. An invalid value only marks the field, it is never written
     * into the Docker flags, so the last valid configuration stays intact.
     *
     * @param value   the raw input value
     * @param minimum the smallest accepted value
     * @param limit   the signal holding the limit to update
     * @param isValid the signal telling the template whether the entered value is valid
     */
    private applyResourceLimit(value: string | number | undefined, minimum: number, limit: WritableSignal<number | undefined>, isValid: WritableSignal<boolean>): void {
        const trimmed = String(value ?? '').trim();
        const parsed = Number(trimmed);
        // an empty field parses as 0, so it has to be rejected before the bounds are checked
        const valueIsValid = trimmed.length > 0 && Number.isInteger(parsed) && parsed >= minimum;
        isValid.set(valueIsValid);
        if (valueIsValid) {
            limit.set(parsed);
            this.parseDockerFlagsToString();
        }
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- input `$event` from the template and the numeric `{ target: { value } }` mock in the spec share no common non-any DOM type
    onCpuCountChange(event: any) {
        this.applyResourceLimit(event.target.value, MIN_DOCKER_CPU_COUNT, this.cpuCount, this.isCpuCountValid);
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- input `$event` from the template and the numeric `{ target: { value } }` mock in the spec share no common non-any DOM type
    onMemoryChange(event: any) {
        this.applyResourceLimit(event.target.value, MIN_DOCKER_MEMORY_MB, this.memory, this.isMemoryValid);
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- input `$event` from the template and the numeric `{ target: { value } }` mock in the spec share no common non-any DOM type
    onMemorySwapChange(event: any) {
        this.applyResourceLimit(event.target.value, MIN_DOCKER_MEMORY_SWAP_MB, this.memorySwap, this.isMemorySwapValid);
    }

    onEnvVarsKeyChange(row: [string, string]) {
        return (newValue: string) => {
            row[0] = newValue;
            this.notifyEnvVarsChanged();
            return row[0];
        };
    }

    onEnvVarsValueChange(row: [string, string]) {
        return (newValue: string) => {
            row[1] = newValue;
            this.notifyEnvVarsChanged();
            return row[1];
        };
    }

    /**
     * Publishes an in-place edit of an environment variable row and re-serializes the Docker flags.
     * <p>
     * The rows are edited in place because the table binds each row object into its cell templates and
     * {@link removeEnvVar} identifies a row by reference, so the row identity has to survive an edit. A mutated row
     * leaves the signal holding the same array, which would notify nothing and leave everything derived from
     * {@link envVars} (such as the flags size check) stale, so a new array over the same rows is published here.
     */
    private notifyEnvVarsChanged(): void {
        this.envVars.update((envVars) => [...envVars]);
        this.parseDockerFlagsToString();
    }

    addEnvVar() {
        this.envVars.update((envVars) => [...envVars, ['', '']]);
    }

    removeEnvVar(row: [string, string]) {
        this.envVars.update((envVars) => envVars.filter((envVar) => envVar !== row));
        this.parseDockerFlagsToString();
    }

    parseDockerFlagsToString() {
        this.dockerFlags = this.currentDockerFlags();
        const serialized = JSON.stringify(this.dockerFlags);
        this.serializedDockerFlags.set(serialized);
        this.programmingExercise()!.buildConfig!.dockerFlags = serialized;
    }

    private currentDockerFlags(): DockerFlags {
        const newEnv: { [key: string]: string } = {};
        this.envVars().forEach(([key, value]) => {
            if (key.trim()) {
                newEnv[key] = value;
            }
        });
        const network = this.network() === '' ? undefined : this.network();
        return { env: newEnv, network: network, cpuCount: this.cpuCount(), memory: this.memory(), memorySwap: this.memorySwap() };
    }

    setIsLanguageSupported() {
        this.isLanguageSupported.set(!NOT_SUPPORTED_NETWORK_DISABLED_LANGUAGES.includes(this.programmingExercise()?.programmingLanguage || ProgrammingLanguage.EMPTY));
    }
}
