import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiTagComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionEmptyComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-empty.component';
import { HyperionArtifactFile, artifactRepoGroups } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';
import { HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

let nextListId = 0;

/** One row with every binding resolved, so no binding in the template calls a method. */
interface FileRow {
    readonly key: string;
    readonly directory: string;
    readonly name: string;
    readonly actionLabelKey?: string;
    /** The most recent write of the whole run: "writing now" while it runs, "written last" once it has stopped. */
    readonly recencyLabelKey?: string;
}

interface RepoGroup {
    readonly repo: HyperionFileChangeRepo;
    readonly labelKey: string;
    readonly labelId: string;
    readonly count: number;
    readonly rows: readonly FileRow[];
}

/** Read-only file inventory, grouped by repository. */
@Component({
    selector: 'jhi-hyperion-file-change-list',
    templateUrl: './hyperion-file-change-list.component.html',
    styleUrl: './hyperion-file-change-list.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, HyperionEmptyComponent, TumUiTagComponent],
})
export class HyperionFileChangeListComponent {
    readonly files = input.required<readonly HyperionArtifactFile[]>();
    /** While the run is going, the newest file is called out as the one being written right now. */
    readonly running = input(false);
    /** Copy for the empty state, owned by the host because only it knows why the list is empty. */
    readonly emptyTitleKey = input('artemisApp.hyperion.generation.artifacts.filesPending');
    readonly emptyDescriptionKey = input<string | undefined>('artemisApp.hyperion.generation.artifacts.filesPendingHint');

    private readonly listId = `hyperion-file-list-${nextListId++}`;

    protected readonly empty = computed(() => this.files().length === 0);
    protected readonly groups = computed<RepoGroup[]>(() => {
        const running = this.running();
        return artifactRepoGroups(this.files()).map((group) => ({
            repo: group.repo,
            labelKey: group.labelKey,
            labelId: `${this.listId}-${group.repo}`,
            count: group.count,
            rows: group.files.map<FileRow>((file) => ({
                key: file.key,
                directory: file.directory,
                name: file.name,
                actionLabelKey: file.action ? `artemisApp.hyperion.generation.artifacts.action.${file.action}` : undefined,
                recencyLabelKey: file.mostRecent
                    ? running
                        ? 'artemisApp.hyperion.generation.artifacts.writingNow'
                        : 'artemisApp.hyperion.generation.artifacts.writtenLast'
                    : undefined,
            })),
        }));
    });
}
