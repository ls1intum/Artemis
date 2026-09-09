import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { TumUiButtonDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionArtifactContentState, HyperionArtifactFile } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';
import { HyperionEmptyComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-empty.component';
import { HyperionFileContentComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-file-content.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DomainChange, DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';
import { DiffInformation, FileStatus } from 'app/programming/shared/utils/diff.utils';

type RepositoryRead = { kind: 'text'; content: string } | { kind: 'missing' } | { kind: 'failed' };
type Preview =
    { kind: 'file'; state: HyperionArtifactContentState } | { kind: 'missing' } | { kind: 'comparison'; diff: DiffInformation; templateMissing: boolean; solutionMissing: boolean };

/** Read-only current repository content. Comparison shows learner work, not a historical generation snapshot. */
@Component({
    selector: 'jhi-hyperion-repository-preview',
    templateUrl: './hyperion-repository-preview.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumUiButtonDirective, HyperionEmptyComponent, HyperionFileContentComponent, GitDiffFileComponent],
})
export class HyperionRepositoryPreviewComponent {
    private readonly repository = inject(CodeEditorRepositoryFileService);
    readonly file = input.required<HyperionArtifactFile>();
    readonly exercise = input.required<ProgrammingExercise>();
    readonly editorLink = input<readonly (string | number)[] | undefined>();

    private readonly identity = computed(() => `${this.exercise().id}:${this.file().key}`);
    protected readonly compare = linkedSignal({ source: this.identity, computation: () => false });
    private readonly retry = signal(0);
    protected readonly preview = signal<Preview>({ kind: 'file', state: { kind: 'loading' } });
    protected readonly canCompare = computed(
        () =>
            (this.file().repo === 'template' || this.file().repo === 'solution') &&
            this.file().path.endsWith('.java') &&
            this.exercise().templateParticipation?.id !== undefined &&
            this.exercise().solutionParticipation?.id !== undefined,
    );

    constructor() {
        effect((onCleanup) => {
            const file = this.file();
            const exercise = this.exercise();
            const compare = this.compare() && this.canCompare();
            this.retry();
            this.preview.set({ kind: 'file', state: { kind: 'loading' } });
            const reads = compare ? [this.read('template', file.path, exercise), this.read('solution', file.path, exercise)] : [this.read(file.repo, file.path, exercise)];
            const subscription = forkJoin(reads).subscribe((results) => {
                if (results.some((result) => result.kind === 'failed')) {
                    this.preview.set({ kind: 'file', state: { kind: 'failed' } });
                } else if (compare) {
                    const [template, solution] = results;
                    this.preview.set({
                        kind: 'comparison',
                        templateMissing: template.kind === 'missing',
                        solutionMissing: solution.kind === 'missing',
                        diff: {
                            title: file.path,
                            originalPath: `template/${file.path}`,
                            modifiedPath: `solution/${file.path}`,
                            originalFileContent: template.kind === 'text' ? template.content : '',
                            modifiedFileContent: solution.kind === 'text' ? solution.content : '',
                            fileStatus: template.kind === 'missing' ? FileStatus.CREATED : solution.kind === 'missing' ? FileStatus.DELETED : FileStatus.UNCHANGED,
                            diffReady: false,
                        },
                    });
                } else {
                    const result = results[0];
                    this.preview.set(
                        result.kind === 'text'
                            ? {
                                  kind: 'file',
                                  state: result.content.length ? { kind: 'text', content: result.content, lineCount: result.content.split('\n').length } : { kind: 'empty' },
                              }
                            : { kind: 'missing' },
                    );
                }
            });
            onCleanup(() => subscription.unsubscribe());
        });
    }

    protected retryRead(): void {
        this.retry.update((value) => value + 1);
    }

    private read(repo: HyperionArtifactFile['repo'], path: string, exercise: ProgrammingExercise): Observable<RepositoryRead> {
        let domain: DomainChange | undefined;
        if (repo === 'tests' && exercise.id !== undefined) {
            domain = [DomainType.TEST_REPOSITORY, exercise];
        } else if (repo === 'template' && exercise.templateParticipation?.id !== undefined) {
            domain = [DomainType.PARTICIPATION, exercise.templateParticipation];
        } else if (repo === 'solution' && exercise.solutionParticipation?.id !== undefined) {
            domain = [DomainType.PARTICIPATION, exercise.solutionParticipation];
        }
        if (!domain) {
            return of({ kind: 'failed' });
        }
        // Supplying the domain per call never changes the code editor's shared selected repository.
        return this.repository.getFile(path, domain).pipe(
            map(({ fileContent }): RepositoryRead => ({ kind: 'text', content: fileContent })),
            catchError((error: unknown): Observable<RepositoryRead> => of({ kind: error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'failed' })),
        );
    }
}
