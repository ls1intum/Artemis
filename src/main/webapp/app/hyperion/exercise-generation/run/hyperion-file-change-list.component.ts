import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiTagComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { REPO_ORDER, displayFileChangePath, fileChangeKey, newestFileChange } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { ExerciseGenerationFileChange, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/** One changed file with everything the template needs already resolved, so no binding calls a method. */
interface FileChangeRow {
    key: string;
    repo: HyperionFileChangeRepo;
    /** Everything up to and including the last separator; this is the part CSS may truncate. */
    directory: string;
    /** The file name, which stays readable at any width. */
    name: string;
    fullPath: string;
    actionLabelKey: string;
    /** Marks the file the agent is writing right now; only ever set on one row, and only while running. */
    writingNow: boolean;
}

interface RepoGroup {
    repo: HyperionFileChangeRepo;
    headingKey: string;
    count: number;
    rows: FileChangeRow[];
}

/**
 * The files a generation run has written, grouped by repository.
 *
 * Purely presentational: it is handed the merged file changes and reports on them, so the run page and any other
 * surface show the same list in the same order.
 */
@Component({
    selector: 'jhi-hyperion-file-change-list',
    templateUrl: './hyperion-file-change-list.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, TumUiTagComponent],
})
export class HyperionFileChangeListComponent {
    readonly files = input.required<readonly ExerciseGenerationFileChange[]>();
    /** While the run is going, the newest file is called out as the one being written right now. */
    readonly running = input(false);

    protected readonly groups = computed<RepoGroup[]>(() => {
        const files = this.files();
        const writingKey = this.running() ? this.newestKey() : undefined;
        const rows = files.map<FileChangeRow>((file) => {
            const displayPath = displayFileChangePath(file);
            const separator = displayPath.lastIndexOf('/');
            const key = fileChangeKey(file);
            return {
                key,
                repo: file.repo,
                directory: separator < 0 ? '' : displayPath.slice(0, separator + 1),
                name: separator < 0 ? displayPath : displayPath.slice(separator + 1),
                fullPath: displayPath,
                actionLabelKey: `artemisApp.hyperion.generation.artifacts.action.${file.action}`,
                writingNow: key === writingKey,
            };
        });
        return REPO_ORDER.map((repo) => {
            const repoRows = rows.filter((row) => row.repo === repo).sort((first, second) => first.fullPath.localeCompare(second.fullPath));
            return { repo, headingKey: `artemisApp.hyperion.generationActivity.repo.${repo}`, count: repoRows.length, rows: repoRows };
        }).filter((group) => group.count > 0);
    });

    protected readonly empty = computed(() => this.files().length === 0);

    private readonly newestKey = computed(() => {
        const newest = newestFileChange(this.files());
        return newest ? fileChangeKey(newest) : undefined;
    });
}
