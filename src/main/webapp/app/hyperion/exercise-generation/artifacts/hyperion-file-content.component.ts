import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { faFileCircleQuestion, faFileLines, faTrashCan, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent, TumUiButtonDirective, TumUiSkeletonComponent, TumUiTagComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionEmptyComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-empty.component';
import { HyperionArtifactContentState, HyperionArtifactFile } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';

const KEY = 'artemisApp.hyperion.generation.artifacts.content';

/** The copy and the glyph for each way a file can have no readable content. One table, so no branch is left unsaid. */
const EXPLANATIONS = {
    deleted: { titleKey: `${KEY}.deletedTitle`, descriptionKey: `${KEY}.deletedHint`, icon: faTrashCan },
    empty: { titleKey: `${KEY}.emptyFileTitle`, descriptionKey: `${KEY}.emptyFileHint`, icon: faFileLines },
    failed: { titleKey: `${KEY}.failedTitle`, descriptionKey: `${KEY}.failedHint`, icon: faTriangleExclamation },
    pendingRun: { titleKey: `${KEY}.pendingRunTitle`, descriptionKey: `${KEY}.pendingRunHint`, icon: faFileCircleQuestion },
    savedToExercise: { titleKey: `${KEY}.savedTitle`, descriptionKey: `${KEY}.savedHint`, icon: faFileLines },
    notRetained: { titleKey: `${KEY}.notRetainedTitle`, descriptionKey: `${KEY}.notRetainedHint`, icon: faFileCircleQuestion },
} as const satisfies Record<Exclude<HyperionArtifactContentState['kind'], 'text' | 'loading'>, unknown>;

/**
 * One generated file: what it is called, what happened to it, and its contents when the server actually has them.
 *
 * This surface answers *"what does this file say?"*, so the file's text is the largest thing on it. Everything else
 * here exists for the case where there is no text, and that case is the common one today: the websocket
 * `FILE_CHANGE` notification carries no content at all, and the retained-artifacts snapshot carries content only for
 * a run that ended without saving. The component therefore never renders "nothing" - every branch of
 * {@link HyperionArtifactContentState} has a sentence saying *why* there is nothing and what would change it.
 *
 * States: **empty** (no file picked) · **loading** (the retained snapshot is being fetched - a static placeholder in
 * a reserved box, never a shimmer) · **text** · **empty file** (a real, zero-byte file, which is not the same fact) ·
 * **deleted** · **failed** (with the caller's retry) · **pending run** (the run is still going and no endpoint serves
 * a file mid-run yet) · **saved to exercise** (the repository is the truth, so read it there) · **not retained**
 * (the run kept nothing and only the list survives).
 */
@Component({
    selector: 'jhi-hyperion-file-content',
    templateUrl: './hyperion-file-content.component.html',
    styleUrl: './hyperion-file-content.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, RouterLink, HyperionEmptyComponent, TumUiButtonComponent, TumUiButtonDirective, TumUiSkeletonComponent, TumUiTagComponent],
})
export class HyperionFileContentComponent {
    readonly file = input<HyperionArtifactFile | undefined>();
    /** Undefined means nothing is selected; the pane then invites a selection rather than reporting on nothing. */
    readonly state = input<HyperionArtifactContentState | undefined>();
    /** Where this file can be read in the code editor, when it can be. Absent means the affordance is not shown at all. */
    readonly editorLink = input<readonly (string | number)[] | undefined>();
    readonly density = input<'comfortable' | 'compact'>('comfortable');
    /** Raised only from the `failed` state; the caller owns the request that failed. */
    readonly retryRequested = output<void>();

    /** A file and its content state, or nothing. The pair is resolved here so the template has no impossible branch. */
    protected readonly selection = computed(() => {
        const file = this.file();
        const state = this.state();
        return file && state ? { file, state } : undefined;
    });
    protected readonly kind = computed(() => this.selection()?.state.kind);
    protected readonly text = computed(() => {
        const state = this.selection()?.state;
        return state?.kind === 'text' ? state.content : undefined;
    });
    protected readonly lineCount = computed(() => {
        const state = this.selection()?.state;
        return state?.kind === 'text' ? state.lineCount : undefined;
    });
    /** The explanation for every non-text, non-loading branch, resolved here so the template holds no lookup. */
    protected readonly explanation = computed(() => {
        const kind = this.kind();
        return kind === undefined || kind === 'text' || kind === 'loading' ? undefined : EXPLANATIONS[kind];
    });
    protected readonly actionLabelKey = computed(() => {
        const action = this.selection()?.file.action;
        return action ? `artemisApp.hyperion.generation.artifacts.action.${action}` : undefined;
    });
    /** The reserved box every state occupies, so the arrival of the text does not move anything below it. */
    protected readonly boxClasses = computed(() => `hyperion-file-content-box${this.density() === 'compact' ? ' hyperion-file-content-compact' : ''}`);
    protected readonly codeClasses = computed(() => `${this.boxClasses()} hyperion-file-content-code`);
    protected readonly emptySize = computed<'small' | 'medium'>(() => (this.density() === 'compact' ? 'small' : 'medium'));

    /**
     * What the editor is told about why the instructor is arriving.
     *
     * `openGenerationActivity` picks the bottom panel, exactly as the run header does. `openGenerationFilePath` is
     * what stops the link from being a link to a repository: the route already names the repository, so handing the
     * editor the repository-relative path makes it open *this* file - the one being read - rather than dropping the
     * instructor at the top of a tree to find it again. A deleted file is deliberately excluded: it is not there to
     * open, and a jump that silently fails is worse than no jump.
     */
    protected readonly editorNavigationState = computed(() => {
        const file = this.selection()?.file;
        const openFile = file && file.action !== 'delete' ? file.path : undefined;
        return openFile ? { openGenerationActivity: true, openGenerationFilePath: openFile } : { openGenerationActivity: true };
    });

    protected readonly fileIcon = faFileLines;
    protected readonly openInEditorKey = `${KEY}.openInEditor`;
    protected readonly retryKey = 'artemisApp.hyperion.generation.artifacts.retryLoad';
}
