import { ChangeDetectionStrategy, Component, computed, input, linkedSignal } from '@angular/core';
import { TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTabsComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionEmptyComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-empty.component';
import { HyperionMarkdownComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-markdown.component';
import { artifactFiles } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';
import { HyperionFileChangeListComponent } from './hyperion-file-change-list.component';
import { ExerciseGenerationFileChange } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/** The approved design and file inventory; source review belongs in the code editor. */
@Component({
    selector: 'jhi-hyperion-artifacts',
    templateUrl: './hyperion-artifacts.component.html',
    styleUrl: './hyperion-artifacts.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        HyperionEmptyComponent,
        HyperionMarkdownComponent,
        HyperionFileChangeListComponent,
        TumUiTabComponent,
        TumUiTabListComponent,
        TumUiTabPanelComponent,
        TumUiTabPanelsComponent,
        TumUiTabsComponent,
    ],
})
export class HyperionArtifactsComponent {
    readonly jobId = input<string>();
    readonly specDocument = input<string>();
    readonly files = input<readonly ExerciseGenerationFileChange[]>([]);
    readonly running = input(false);
    readonly terminal = input(false);
    readonly savedToExercise = input(false);

    protected readonly activeTab = linkedSignal({ source: this.jobId, computation: (): string => 'spec' });
    protected readonly artifacts = computed(() => artifactFiles(this.files()));
    protected readonly fileCount = computed(() => this.artifacts().length);
    protected readonly hasSpec = computed(() => !!this.specDocument()?.trim());
    protected readonly hintKey = computed(() => `artemisApp.hyperion.generation.artifacts.${this.savedToExercise() ? 'savedHint' : 'notSavedHint'}`);
    protected readonly specEmptyTitleKey = computed(() => `artemisApp.hyperion.generation.artifacts.${this.terminal() ? 'specNone' : 'specPending'}`);
    protected readonly specEmptyHintKey = computed(() => `artemisApp.hyperion.generation.artifacts.${this.terminal() ? 'notKeptHint' : 'specPendingHint'}`);
    protected readonly filesEmptyTitleKey = computed(() => `artemisApp.hyperion.generation.artifacts.${this.terminal() ? 'filesNone' : 'filesPending'}`);
    protected readonly filesEmptyHintKey = computed(() => `artemisApp.hyperion.generation.artifacts.${this.terminal() ? 'notKeptHint' : 'filesPendingHint'}`);

    protected onTabChange(value: string | number | undefined): void {
        if (value === 'spec' || value === 'files') {
            this.activeTab.set(value);
        }
    }
}
