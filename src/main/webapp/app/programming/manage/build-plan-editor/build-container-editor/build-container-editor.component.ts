import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
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

    readonly remove = model<void>();

    /** the repository types an instructor can check out into a container, in the order they are offered */
    protected readonly repositoryTypes = Object.keys(BUILD_CONTAINER_REPOSITORY_TYPE) as BuildContainerRepositoryType[];

    readonly isNamePatternValid = computed(() => BUILD_CONTAINER_NAME_PATTERN.test(this.container().name));

    readonly isNameUnique = computed(() => {
        const name = this.container().name.toLowerCase();
        return !this.otherContainerNames().some((otherName) => otherName.toLowerCase() === name);
    });

    readonly isNameValid = computed(() => this.isNamePatternValid() && this.isNameUnique());

    readonly nameValidationMessageKey = computed(() => {
        if (!this.isNamePatternValid()) {
            return 'artemisApp.programmingExercise.buildContainersEditor.containerNameInvalid';
        }
        return this.isNameUnique() ? undefined : 'artemisApp.programmingExercise.buildContainersEditor.containerNameDuplicate';
    });

    /**
     * A container that scopes no repositories checks out the repositories configured on the exercise, which is what a
     * build plan without containers does. Selecting repositories opts a container into the stricter scoping.
     */
    readonly scopesRepositories = computed(() => this.container().repositories !== undefined);

    setName(name: string): void {
        this.container.update((container) => ({ ...container, name }));
    }

    setDockerImage(dockerImage: string): void {
        this.container.update((container) => ({ ...container, dockerImage }));
    }

    setPhases(phases: BuildPhase[]): void {
        this.container.update((container) => ({ ...container, phases }));
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
            return {
                ...container,
                repositories: selected ? [...repositories, { type }] : repositories.filter((repository) => repository.type !== type),
            };
        });
    }

    /**
     * Switches between checking out the repositories configured on the exercise and scoping the repositories explicitly.
     */
    toggleRepositoryScoping(scoped: boolean): void {
        this.container.update((container) => ({ ...container, repositories: scoped ? [] : undefined }));
    }
}
