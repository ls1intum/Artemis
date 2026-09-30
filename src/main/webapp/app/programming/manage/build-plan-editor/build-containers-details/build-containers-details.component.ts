import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';
import { BuildContainer } from 'app/programming/shared/entities/build-plan-phases.model';

/**
 * Read-only view of the containers of a build plan, one card per container with its name, Docker image, the
 * repositories checked out into it, the Docker flags it overrides and its build phases. Shown on the exercise details page, where the build plan
 * cannot be edited; the editable counterpart is {@code BuildContainerEditorComponent}.
 */
@Component({
    selector: 'jhi-build-containers-details',
    templateUrl: './build-containers-details.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, BuildPhasesEditorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildContainersDetailsComponent {
    readonly containers = input.required<BuildContainer[]>();
    readonly isExamMode = input(false);
    /** the image of the exercise's language default, which a container without an image of its own is built with */
    readonly defaultDockerImage = input<string | undefined>();

    /** the names of the environment variables a container sets on top of the exercise's, or undefined if it sets none */
    protected envVarNames(container: BuildContainer): string | undefined {
        return Object.keys(container.dockerFlags?.env ?? {}).join(', ') || undefined;
    }
}
