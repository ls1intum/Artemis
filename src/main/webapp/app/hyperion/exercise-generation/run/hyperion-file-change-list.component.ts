import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
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
    readonly path: string;
    readonly actionLabelKey?: string;
    /** The most recent write of the whole run: "writing now" while it runs, "written last" once it has stopped. */
    readonly recencyLabelKey?: string;
    readonly selected: boolean;
    readonly actionable: boolean;
    readonly file: HyperionArtifactFile;
}

interface RepoGroup {
    readonly repo: HyperionFileChangeRepo;
    readonly labelKey: string;
    readonly labelId: string;
    readonly count: number;
    readonly rows: readonly FileRow[];
}

/**
 * The files a generation run has written, grouped by repository.
 *
 * One list, two hosts: the run page's artifact browser, where picking a row shows that file's contents, and the code
 * editor's AI panel, where picking a row opens the file in the editor. Both get the same rows in the same order,
 * because a file that reads as `tests/…/FooTest.java` in one place and `Tests · FooTest.java` in the other is two
 * vocabularies for one fact.
 *
 * The row is the affordance, not a button inside it, and a row is only interactive where the host can actually do
 * something with it: `actionableKeys` left unset means every row acts, and a host that can act on none of them
 * passes an empty array rather than rendering dead controls.
 *
 * States: **empty** (the caller supplies the sentence, because only it knows whether "no files yet" means the agent
 * has not started or that the run kept nothing) · **populated** · **populated with one row marked as the newest
 * write**. There is no loading state: the list streams, so a partial list is the truth rather than a placeholder.
 */
@Component({
    selector: 'jhi-hyperion-file-change-list',
    templateUrl: './hyperion-file-change-list.component.html',
    styleUrl: './hyperion-file-change-list.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgTemplateOutlet, ArtemisTranslatePipe, TranslateDirective, HyperionEmptyComponent, TumUiTagComponent],
})
export class HyperionFileChangeListComponent {
    readonly files = input.required<readonly HyperionArtifactFile[]>();
    /** While the run is going, the newest file is called out as the one being written right now. */
    readonly running = input(false);
    /** `compact` is the docked-panel tier. Density is an input, never a second component. */
    readonly density = input<'comfortable' | 'compact'>('comfortable');
    readonly selectedKey = input<string | undefined>();
    /** Which rows the host can act on. `undefined` means all of them; an empty array means none, and no row is a control. */
    readonly actionableKeys = input<readonly string[] | undefined>();
    /** Copy for the empty state, owned by the host because only it knows why the list is empty. */
    readonly emptyTitleKey = input('artemisApp.hyperion.generation.artifacts.filesPending');
    readonly emptyDescriptionKey = input<string | undefined>('artemisApp.hyperion.generation.artifacts.filesPendingHint');
    readonly fileSelected = output<HyperionArtifactFile>();

    private readonly listId = `hyperion-file-list-${nextListId++}`;

    protected readonly empty = computed(() => this.files().length === 0);
    protected readonly rowClass = computed(() => (this.density() === 'compact' ? 'hyperion-artifact-row hyperion-artifact-row-compact' : 'hyperion-artifact-row'));
    protected readonly staticRowClass = computed(() => `${this.rowClass()} hyperion-artifact-row-static`);
    protected readonly emptySize = computed<'small' | 'medium'>(() => (this.density() === 'compact' ? 'small' : 'medium'));

    protected readonly groups = computed<RepoGroup[]>(() => {
        const running = this.running();
        const selectedKey = this.selectedKey();
        const actionableKeys = this.actionableKeys();
        return artifactRepoGroups(this.files()).map((group) => ({
            repo: group.repo,
            labelKey: group.labelKey,
            labelId: `${this.listId}-${group.repo}`,
            count: group.count,
            rows: group.files.map<FileRow>((file) => ({
                key: file.key,
                directory: file.directory,
                name: file.name,
                path: file.path,
                actionLabelKey: file.action ? `artemisApp.hyperion.generation.artifacts.action.${file.action}` : undefined,
                recencyLabelKey: file.mostRecent
                    ? running
                        ? 'artemisApp.hyperion.generation.artifacts.writingNow'
                        : 'artemisApp.hyperion.generation.artifacts.writtenLast'
                    : undefined,
                selected: file.key === selectedKey,
                actionable: actionableKeys === undefined || actionableKeys.includes(file.key),
                file,
            })),
        }));
    });

    protected select(row: FileRow): void {
        if (row.actionable) {
            this.fileSelected.emit(row.file);
        }
    }
}
