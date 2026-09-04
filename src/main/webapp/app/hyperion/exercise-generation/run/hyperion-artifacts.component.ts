import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
    TumUiButtonComponent,
    TumUiMessageComponent,
    TumUiSkeletonComponent,
    TumUiTabComponent,
    TumUiTabListComponent,
    TumUiTabPanelComponent,
    TumUiTabPanelsComponent,
    TumUiTabsComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HyperionEmptyComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-empty.component';
import { HyperionFileContentComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-file-content.component';
import { HyperionMarkdownComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-markdown.component';
import { HyperionFileChangeListComponent } from 'app/hyperion/exercise-generation/run/hyperion-file-change-list.component';
import { HyperionArtifactContentContext, HyperionArtifactFile, artifactContentState, artifactFiles } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';
import { ExerciseGenerationFileChange, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { ExerciseGenerationRetainedArtifacts } from 'app/openapi/model/exercise-generation-retained-artifacts';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

/** The three things a run produces that an instructor reads. Values double as the tab identifiers. */
export type HyperionArtifactTab = 'statement' | 'spec' | 'files';

/**
 * What a generation run has produced: the problem statement, the design specification, and the files.
 *
 * This surface answers *"what did it actually write?"*, so the rendered problem statement is the largest thing on it.
 * Three collapsible panels used to answer that question, and they folded themselves shut the moment the run
 * finished - hiding the one artifact that has to be reviewed before release at exactly the moment it became the
 * answer. They are one tab set now: the same shortening, achieved by removing a container level instead of by
 * hiding output, and one level of disclosure rather than three.
 *
 * File-change events remain deliberately lightweight. Each event invalidates the owner-only, bounded candidate
 * snapshot, which supplies exact file content and also makes reconnects independent of websocket replay history.
 *
 * States: **empty** (nothing written yet - each tab says what fills it) · **loading** (the retained snapshot, as
 * placeholders in a reserved box) · **running** (statement and spec usually absent, files streaming) ·
 * **failed to load** (a message with a retry, with the tab still selectable) · **terminal, retained** (statement,
 * spec and file contents all present) · **terminal, saved to the exercise** (the exercise's own statement, and the
 * repositories are the truth for the files) · **terminal, nothing retained** (the file list survives, the contents
 * do not).
 */
@Component({
    selector: 'jhi-hyperion-artifacts',
    templateUrl: './hyperion-artifacts.component.html',
    styleUrl: './hyperion-artifacts.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        NgTemplateOutlet,
        TranslateDirective,
        HyperionEmptyComponent,
        HyperionFileChangeListComponent,
        HyperionFileContentComponent,
        HyperionMarkdownComponent,
        TumUiButtonComponent,
        TumUiMessageComponent,
        TumUiSkeletonComponent,
        TumUiTabComponent,
        TumUiTabListComponent,
        TumUiTabPanelComponent,
        TumUiTabPanelsComponent,
        TumUiTabsComponent,
    ],
})
export class HyperionArtifactsComponent {
    private readonly api = inject(HyperionExerciseGenerationApi);
    private readonly destroyRef = inject(DestroyRef);

    readonly exerciseId = input<number | undefined>();
    /**
     * The exercise this run belongs to, when the host has it.
     *
     * Only used to build the links that open a repository in the code editor. Absent means those links are not
     * rendered at all rather than rendered disabled: an affordance that cannot work for this viewer is not shown.
     */
    readonly exercise = input<ProgrammingExercise | undefined>();
    /** The design document the agent wrote before touching code, as reported by the run status. */
    readonly specDocument = input<string | undefined>();
    /** The exercise's own problem statement, set only once a run actually saved one. */
    readonly savedProblemStatement = input<string | undefined>();
    readonly files = input<readonly ExerciseGenerationFileChange[]>([]);
    readonly running = input(false);
    /** Whether the run has ended, however it ended. */
    readonly terminal = input(false);
    /** Whether the run wrote its result into the exercise, which decides where the artifacts have to be read from. */
    readonly savedToExercise = input(false);

    private readonly retained = signal<ExerciseGenerationRetainedArtifacts | undefined>(undefined);
    protected readonly retainedLoadFailed = signal(false);
    protected readonly retainedLoading = signal(false);
    private retainedRequestKey?: string;
    private retainedRequestSequence = 0;

    protected readonly activeTab = signal<HyperionArtifactTab>('statement');
    /** Which file the content pane is showing. Held here, not in the tab panel, so switching tabs cannot lose it. */
    protected readonly selectedFileKey = signal<string | undefined>(undefined);

    /** A run that saved its work has no retained draft; the exercise's own statement is then the only honest source. */
    private readonly saved = computed(() => this.savedToExercise() || this.savedProblemStatement() !== undefined);

    /** The live exercise wins: once the draft is saved, what the instructor sees must be what is actually stored. */
    protected readonly problemStatement = computed(() => this.savedProblemStatement() ?? this.retained()?.problemStatement);
    protected readonly spec = computed(() => this.specDocument() ?? this.retained()?.specDocument);

    protected readonly hasSpec = computed(() => (this.spec()?.trim().length ?? 0) > 0);
    protected readonly hasProblemStatement = computed(() => (this.problemStatement()?.trim().length ?? 0) > 0);

    /**
     * Every file the client knows about, merged from the change events and the retained snapshot.
     *
     * The snapshot is a union partner rather than a decoration: the change events ride a bounded replay window, so a
     * run reopened later can have content for a file whose event is long gone, and dropping it would hide a file
     * that exists.
     */
    protected readonly artifacts = computed(() => artifactFiles(this.files(), this.retained()?.files ?? []));
    protected readonly fileCount = computed(() => this.artifacts().length);

    protected readonly selectedFile = computed<HyperionArtifactFile | undefined>(() => {
        const key = this.selectedFileKey();
        return key === undefined ? undefined : this.artifacts().find((file) => file.key === key);
    });

    private readonly contentContext = computed<HyperionArtifactContentContext>(() => ({
        loading: this.retainedLoading(),
        failed: this.retainedLoadFailed(),
        running: this.running(),
        savedToExercise: this.saved(),
    }));

    protected readonly contentState = computed(() => {
        const file = this.selectedFile();
        return file ? artifactContentState(file, this.contentContext()) : undefined;
    });

    /** The repository the selected file lives in, opened in the code editor - when this viewer can get there at all. */
    protected readonly selectedEditorLink = computed(() => {
        const file = this.selectedFile();
        return file ? this.editorLink(file.repo) : undefined;
    });

    /** Placeholders rather than prose: a markdown tab is loading only while the retained snapshot is in flight. */
    protected readonly markdownLoading = computed(() => this.retainedLoading());

    /**
     * The sentence under the heading. It has to change when the run saves: "none of this is in your exercise yet" is
     * true for the whole life of a draft and false the moment the run writes it in, and a stale reassurance about
     * where an instructor's work is, is worse than none.
     */
    protected readonly hintKey = computed(() => (this.saved() ? 'artemisApp.hyperion.generation.artifacts.savedHint' : 'artemisApp.hyperion.generation.artifacts.notSavedHint'));

    /**
     * An empty tab means two different things before and after the run ends, so each gets two sentences.
     *
     * "Artemis writes it near the end of the design stage" is a promise, and it is a false one once the run is over.
     * A finished run that produced nothing says so instead.
     */
    protected readonly filesEmptyTitleKey = computed(() => this.emptyKey('filesNone', 'filesPending'));
    protected readonly filesEmptyHintKey = computed(() => this.emptyKey('notKeptHint', 'filesPendingHint'));
    protected readonly statementEmptyTitleKey = computed(() => this.emptyKey('statementNone', 'statementPending'));
    protected readonly statementEmptyHintKey = computed(() => this.emptyKey('notKeptHint', 'statementPendingHint'));
    protected readonly specEmptyTitleKey = computed(() => this.emptyKey('specNone', 'specPending'));
    protected readonly specEmptyHintKey = computed(() => this.emptyKey('notKeptHint', 'specPendingHint'));

    private emptyKey(whenTerminal: string, whileRunning: string): string {
        return `artemisApp.hyperion.generation.artifacts.${this.terminal() ? whenTerminal : whileRunning}`;
    }

    constructor() {
        // A change event is an invalidation, not a source payload. Refetching the bounded snapshot keeps source out
        // of the shared websocket and gives a reconnecting browser the same current state as a connected one.
        effect(() => {
            const exerciseId = this.exerciseId();
            const files = this.files();
            const lastChange = files.at(-1);
            const revision = lastChange ? `${files.length}:${lastChange.timestamp}:${lastChange.path}:${lastChange.action}` : 'initial';
            const wanted = !this.saved() && exerciseId !== undefined && (this.running() || this.terminal());
            if (wanted) {
                untracked(() => this.loadRetainedArtifacts(exerciseId, revision));
            }
        });

        // The opening tab is decided once, from what exists at the moment the browser first settles, and never
        // revised: a tab that changes under a reader because a file arrived is the surface moving the text being
        // read. A live run therefore opens on Files - which is the tab that fills while it runs - and a finished run
        // opens on the artifact that has to be reviewed before release.
        effect(() => {
            const preferred = this.preferredTab();
            if (preferred && !this.openingTabChosen) {
                this.openingTabChosen = true;
                untracked(() => this.activeTab.set(preferred));
            }
        });
    }

    private openingTabChosen = false;

    /** `undefined` while the answer would be provisional, i.e. while the retained snapshot is still being fetched. */
    private readonly preferredTab = computed<HyperionArtifactTab | undefined>(() => {
        if (this.retainedLoading()) {
            return undefined;
        }
        if (this.hasProblemStatement()) {
            return 'statement';
        }
        return this.hasSpec() ? 'spec' : 'files';
    });

    /** The tabs component speaks `string | number`; narrowing happens here so the template holds no cast. */
    protected onTabChange(value: string | number | undefined): void {
        if (value === 'statement' || value === 'spec' || value === 'files') {
            // A tab the instructor picked themselves is theirs to keep: nothing arriving later moves them off it.
            this.openingTabChosen = true;
            this.activeTab.set(value);
        }
    }

    protected selectFile(file: HyperionArtifactFile): void {
        this.selectedFileKey.set(file.key);
    }

    /** Retries a failed fetch without waiting for another file-change event. */
    protected retryRetainedArtifacts(): void {
        this.retainedRequestKey = undefined;
        const exerciseId = this.exerciseId();
        if (exerciseId !== undefined) {
            this.loadRetainedArtifacts(exerciseId, `retry:${Date.now()}`);
        }
    }

    private editorLink(repo: HyperionFileChangeRepo): readonly (string | number)[] | undefined {
        const exercise = this.exercise();
        const courseId = getCourseId(exercise);
        const exerciseId = this.exerciseId();
        if (courseId === undefined || exerciseId === undefined) {
            return undefined;
        }
        const base = ['/course-management', courseId, 'programming-exercises', exerciseId, 'code-editor'] as const;
        // The test repository is addressed by name; the other two are addressed by their participation, which an
        // exercise loaded without its participations does not have - and then there is no link rather than a broken one.
        switch (repo) {
            case 'tests':
                return [...base, RepositoryType.TESTS, 'test'];
            case 'solution': {
                const participationId = exercise?.solutionParticipation?.id;
                return participationId === undefined ? undefined : [...base, RepositoryType.SOLUTION, participationId];
            }
            case 'template': {
                const participationId = exercise?.templateParticipation?.id;
                return participationId === undefined ? undefined : [...base, RepositoryType.TEMPLATE, participationId];
            }
            default:
                return undefined;
        }
    }

    private loadRetainedArtifacts(exerciseId: number, revision: string): void {
        const requestKey = `${exerciseId}:${revision}`;
        if (this.retainedRequestKey === requestKey) {
            return;
        }
        this.retainedRequestKey = requestKey;
        const sequence = ++this.retainedRequestSequence;
        this.retainedLoading.set(this.retained() === undefined);
        this.retainedLoadFailed.set(false);
        this.api
            .getRetainedGenerationArtifacts(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (artifacts) => {
                    if (sequence !== this.retainedRequestSequence) {
                        return;
                    }
                    this.retainedLoading.set(false);
                    this.retained.set(artifacts);
                },
                // Never swallowed: an instructor who cannot see the retained draft must be told why. A 404 is the
                // exception — it is the server saying this run kept nothing, which the "nothing retained" copy covers.
                error: (error: unknown) => {
                    if (sequence !== this.retainedRequestSequence) {
                        return;
                    }
                    this.retainedLoading.set(false);
                    this.retainedLoadFailed.set(!(error instanceof HttpErrorResponse && error.status === 404));
                },
            });
    }
}
