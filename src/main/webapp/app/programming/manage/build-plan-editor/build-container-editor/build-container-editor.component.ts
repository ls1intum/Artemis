import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';
import {
    BUILD_CONTAINER_NAME_PATTERN,
    BUILD_CONTAINER_REPOSITORY_TYPE,
    BuildContainer,
    BuildContainerDockerFlags,
    BuildContainerRepositoryType,
    BuildPhase,
    MIN_DOCKER_CPU_COUNT,
    MIN_DOCKER_MEMORY_MB,
    MIN_DOCKER_MEMORY_SWAP_MB,
    isDockerResourceLimitValid,
} from 'app/programming/shared/entities/build-plan-phases.model';

type EnvVars = { [key: string]: string };

/** the environment variables the rows stand for: a row without a key is one the instructor has not filled in yet */
function envVarsOf(rows: [string, string][]): EnvVars | undefined {
    const env: EnvVars = {};
    for (const [key, value] of rows) {
        if (key.trim()) {
            env[key] = value;
        }
    }
    return Object.keys(env).length > 0 ? env : undefined;
}

/**
 * Edits a single build container: its name, the Docker image it runs, the repositories checked out into it, the Docker
 * flags it overrides, and its build phases.
 */
@Component({
    selector: 'jhi-build-container-editor',
    templateUrl: './build-container-editor.component.html',
    imports: [FormsModule, TumAetUiButtonComponent, TumAetUiTooltipDirective, TranslateDirective, ArtemisTranslatePipe, HelpIconComponent, BuildPhasesEditorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildContainerEditorComponent {
    protected readonly faTrash = faTrash;
    protected readonly faPlus = faPlus;

    readonly container = model.required<BuildContainer>();
    readonly isExamMode = input(false);
    /** the names of the other containers of the build plan, used to detect duplicates */
    readonly otherContainerNames = input<string[]>([]);
    readonly canRemove = input(false);
    // the language default image, shown as a placeholder while the field is empty instead of being written into it: an
    // empty image means the container follows the exercise's language default at build time (see buildConfigForContainer)
    readonly dockerImagePlaceholder = input<string>('');
    // the exercise timeout, shown as the placeholder of the container timeout: an empty field means the container uses it
    readonly timeoutPlaceholder = input<number | undefined>(undefined);
    readonly timeoutMinValue = input<number | undefined>(undefined);
    readonly timeoutMaxValue = input<number | undefined>(undefined);
    // the resource limits of the exercise, shown as the placeholders of the container's limits: an empty field means the
    // container uses them
    readonly exerciseDockerFlags = input<BuildContainerDockerFlags>({});
    // the custom networks a container may select on this instance; undefined while the exercise's language does not
    // support selecting a network, which hides the field
    readonly customNetworks = input<string[] | undefined>(undefined);

    /** emitted when the instructor asks to remove this container from the build plan */
    readonly remove = output<void>();

    /** the repository types an instructor can check out into a container, in the order they are offered */
    protected readonly repositoryTypes = Object.keys(BUILD_CONTAINER_REPOSITORY_TYPE) as BuildContainerRepositoryType[];

    readonly isNamePatternValid = computed(() => BUILD_CONTAINER_NAME_PATTERN.test(this.container().name));

    readonly isNameUnique = computed(() => {
        const name = this.container().name.toLowerCase();
        return !this.otherContainerNames().some((otherName) => otherName.toLowerCase() === name);
    });

    readonly isNameValid = computed(() => this.isNamePatternValid() && this.isNameUnique());

    /** an unset timeout is valid (the exercise timeout applies); a set one has to lie within the bounds of the instance */
    readonly isTimeoutValid = computed(() => {
        const timeout = this.container().timeoutSeconds;
        if (timeout === undefined) {
            return true;
        }
        const min = this.timeoutMinValue();
        const max = this.timeoutMaxValue();
        return Number.isInteger(timeout) && timeout > 0 && (min === undefined || timeout >= min) && (max === undefined || timeout <= max);
    });

    readonly nameValidationMessageKey = computed(() => {
        if (!this.isNamePatternValid()) {
            return 'artemisApp.programmingExercise.buildContainersEditor.containerNameInvalid';
        }
        return this.isNameUnique() ? undefined : 'artemisApp.programmingExercise.buildContainersEditor.containerNameDuplicate';
    });

    /**
     * A container that scopes no repositories checks out the repositories configured on the exercise, which is what a
     * build plan without containers does. Selecting repositories opts a container into the stricter scoping. A plan
     * that comes straight from the server, such as a template, carries an explicit null for an unscoped container.
     */
    readonly scopesRepositories = computed(() => this.container().repositories != undefined);

    /** a container without flags of its own runs with the Docker flags configured on the exercise */
    readonly overridesDockerFlags = computed(() => this.container().dockerFlags != undefined);

    readonly isCpuCountValid = computed(() => isDockerResourceLimitValid(this.container().dockerFlags?.cpuCount, MIN_DOCKER_CPU_COUNT));
    readonly isMemoryValid = computed(() => isDockerResourceLimitValid(this.container().dockerFlags?.memory, MIN_DOCKER_MEMORY_MB));
    readonly isMemorySwapValid = computed(() => isDockerResourceLimitValid(this.container().dockerFlags?.memorySwap, MIN_DOCKER_MEMORY_SWAP_MB));

    /**
     * The environment variables as the rows the instructor edits. The container stores them as a map, which can hold
     * neither a row whose key is still empty nor the order of the rows, so the rows are kept here and only rebuilt from
     * the container when its variables are not the ones the rows stand for, i.e. when they changed from outside.
     */
    protected readonly envVarRows = linkedSignal<EnvVars | undefined, [string, string][]>({
        source: () => this.container().dockerFlags?.env,
        computation: (env, previous) => {
            if (previous && JSON.stringify(envVarsOf(previous.value) ?? {}) === JSON.stringify(env ?? {})) {
                return previous.value;
            }
            return Object.entries(env ?? {});
        },
    });

    setName(name: string): void {
        this.container.update((container) => cloneWith(container, { name }));
    }

    setDockerImage(dockerImage: string): void {
        this.container.update((container) => cloneWith(container, { dockerImage }));
    }

    /** an emptied field removes the override, so the container follows the exercise timeout again */
    setTimeoutSeconds(value: number | string | null): void {
        const timeoutSeconds = value === null || value === '' ? undefined : Number(value);
        this.container.update((container) => cloneWith(container, { timeoutSeconds }));
    }

    setPhases(phases: BuildPhase[]): void {
        this.container.update((container) => cloneWith(container, { phases }));
    }

    isRepositorySelected(type: BuildContainerRepositoryType): boolean {
        return !!this.container().repositories?.some((repository) => repository.type === type);
    }

    /**
     * Adds or removes a repository from the container. Deselecting the last repository does not fall back to the
     * repositories of the exercise, so that a container cannot silently widen its scope again.
     */
    toggleRepository(type: BuildContainerRepositoryType, selected: boolean): void {
        this.container.update((container) => {
            const repositories = container.repositories ?? [];
            return cloneWith(container, {
                repositories: selected ? [...repositories, { type }] : repositories.filter((repository) => repository.type !== type),
            });
        });
    }

    /**
     * Switches between checking out the repositories configured on the exercise and scoping the repositories explicitly.
     */
    toggleRepositoryScoping(scoped: boolean): void {
        this.container.update((container) => cloneWith(container, { repositories: scoped ? [] : undefined }));
    }

    /**
     * Switches between running with the Docker flags of the exercise and overriding them for this container. Switching
     * the override off discards the container's flags.
     */
    toggleDockerFlags(overridden: boolean): void {
        this.envVarRows.set([]);
        this.container.update((container) => cloneWith(container, { dockerFlags: overridden ? {} : undefined }));
    }

    /** the empty option removes the override, so the container joins the network of the exercise again */
    setNetwork(network: string): void {
        this.updateDockerFlags({ network: network || undefined });
    }

    setCpuCount(value: number | string | null): void {
        this.updateDockerFlags({ cpuCount: this.resourceLimitOf(value) });
    }

    setMemory(value: number | string | null): void {
        this.updateDockerFlags({ memory: this.resourceLimitOf(value) });
    }

    setMemorySwap(value: number | string | null): void {
        this.updateDockerFlags({ memorySwap: this.resourceLimitOf(value) });
    }

    addEnvVar(): void {
        this.envVarRows.update((rows) => [...rows, ['', '']]);
    }

    removeEnvVar(index: number): void {
        this.setEnvVarRows(this.envVarRows().filter((_, currentIndex) => currentIndex !== index));
    }

    setEnvVarKey(index: number, key: string): void {
        this.setEnvVarRows(this.envVarRows().map((row, currentIndex) => (currentIndex === index ? [key, row[1]] : row)));
    }

    setEnvVarValue(index: number, value: string): void {
        this.setEnvVarRows(this.envVarRows().map((row, currentIndex) => (currentIndex === index ? [row[0], value] : row)));
    }

    private setEnvVarRows(rows: [string, string][]): void {
        this.envVarRows.set(rows);
        this.updateDockerFlags({ env: envVarsOf(rows) });
    }

    /** an emptied field removes the override, so the container follows the exercise's limit again */
    private resourceLimitOf(value: number | string | null): number | undefined {
        return value === null || value === '' ? undefined : Number(value);
    }

    private updateDockerFlags(overrides: BuildContainerDockerFlags): void {
        this.container.update((container) => cloneWith(container, { dockerFlags: cloneWith(container.dockerFlags ?? {}, overrides) }));
    }
}
