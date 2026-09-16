import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';
import {
    BUILD_CONTAINER_NAME_PATTERN,
    BUILD_CONTAINER_REPOSITORY_TYPE,
    BuildContainer,
    BuildContainerRepositoryType,
    BuildPhase,
} from 'app/programming/shared/entities/build-plan-phases.model';

/**
 * Edits a single build container: its name, the Docker image it runs, the repositories checked out into it, and its
 * build phases.
 */
@Component({
    selector: 'jhi-build-container-editor',
    templateUrl: './build-container-editor.component.html',
    imports: [FormsModule, TumUiButtonComponent, TumUiTooltipDirective, TranslateDirective, ArtemisTranslatePipe, HelpIconComponent, BuildPhasesEditorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildContainerEditorComponent {
    protected readonly faTrash = faTrash;

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

    readonly remove = model<void>();

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
}
