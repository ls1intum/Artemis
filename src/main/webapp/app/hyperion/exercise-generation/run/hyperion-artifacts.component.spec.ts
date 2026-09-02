import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionArtifactsComponent } from 'app/hyperion/exercise-generation/run/hyperion-artifacts.component';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import { ExerciseGenerationRetainedArtifacts } from 'app/openapi/model/exercise-generation-retained-artifacts';
import { ExerciseGenerationFileChange, HyperionFileChangeAction, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';

const EXERCISE_ID = 42;

function change(
    repo: HyperionFileChangeRepo,
    path: string,
    { action = 'write' as HyperionFileChangeAction, turn = 1, timestamp = '2026-07-13T09:00:00Z' } = {},
): ExerciseGenerationFileChange {
    return { type: 'FILE_CHANGE', repo, path, action, turn, timestamp };
}

function retainedArtifacts(overrides: Partial<ExerciseGenerationRetainedArtifacts> = {}): ExerciseGenerationRetainedArtifacts {
    return { jobId: 'j1', completeness: 'COMPLETE', files: [], ...overrides };
}

/** An exercise with the participations the solution and template links need, unless a test takes them away. */
function exercise(overrides: Partial<ProgrammingExercise> = {}): ProgrammingExercise {
    const course = { id: 7 } as Course;
    return {
        id: EXERCISE_ID,
        course,
        solutionParticipation: { id: 11 },
        templateParticipation: { id: 12 },
        ...overrides,
    } as ProgrammingExercise;
}

describe('HyperionArtifactsComponent', () => {
    let fixture: ComponentFixture<HyperionArtifactsComponent>;
    let getRetained: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        getRetained = vi.fn().mockReturnValue(of(retainedArtifacts()));
        await TestBed.configureTestingModule({
            imports: [HyperionArtifactsComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HyperionExerciseGenerationApi, useValue: { getRetainedGenerationArtifacts: getRetained } },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionArtifactsComponent);
    });

    function render(inputs: Record<string, unknown> = {}): HTMLElement {
        for (const [name, value] of Object.entries(inputs)) {
            fixture.componentRef.setInput(name, value);
        }
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    function set(inputs: Record<string, unknown>): HTMLElement {
        for (const [name, value] of Object.entries(inputs)) {
            fixture.componentRef.setInput(name, value);
        }
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    function query(host: HTMLElement, testId: string): HTMLElement | null {
        return host.querySelector(`[data-testid="${testId}"]`);
    }

    function activePanel(host: HTMLElement): string | undefined {
        return host.querySelector('tum-ui-tab-panel[data-state="active"]')?.id;
    }

    describe('one browser instead of three panels', () => {
        it('offers the three artifacts as tabs and never folds the answer away on a finished run', () => {
            const host = render({ terminal: true, savedToExercise: true, savedProblemStatement: '# Statement' });

            expect(query(host, 'hyperion-artifacts-tab-statement')).not.toBeNull();
            expect(query(host, 'hyperion-artifacts-tab-spec')).not.toBeNull();
            expect(query(host, 'hyperion-artifacts-tab-files')).not.toBeNull();
            expect(query(host, 'hyperion-artifacts-statement')).not.toBeNull();
        });

        it('counts the files on the tab, so the count does not need the tab to be opened', () => {
            const host = render({ files: [change('solution', 'solution/A.java'), change('tests', 'tests/ATest.java')] });

            expect(query(host, 'hyperion-artifacts-tab-files')!.textContent).toContain('2');
        });

        it('keeps every panel in the DOM, so a trip to another tab does not reset it', () => {
            const host = render({ terminal: true, savedProblemStatement: '# Statement', files: [change('solution', 'solution/A.java')] });

            expect(host.querySelectorAll('tum-ui-tab-panel')).toHaveLength(3);
            expect(query(host, 'hyperion-artifacts-statement')).not.toBeNull();
            expect(query(host, 'hyperion-file-change-list')).not.toBeNull();
            expect(host.querySelectorAll('tum-ui-tab-panel[data-state="inactive"]')[0].getAttribute('inert')).toBe('');
        });
    });

    describe('which tab opens', () => {
        it('opens on the problem statement, the artifact that has to be reviewed before release', () => {
            const host = render({ terminal: true, savedProblemStatement: '# Statement', specDocument: '# Spec' });

            expect(activePanel(host)).toBe(query(host, 'hyperion-artifacts-tab-statement')!.getAttribute('aria-controls'));
        });

        it('falls back to the design spec when no statement exists yet', () => {
            const host = render({ specDocument: '# Spec' });

            expect(activePanel(host)).toBe(query(host, 'hyperion-artifacts-tab-spec')!.getAttribute('aria-controls'));
        });

        it('falls back to the files, which is the tab that fills while a run goes', () => {
            const host = render({ running: true, files: [change('solution', 'solution/A.java')] });

            expect(activePanel(host)).toBe(query(host, 'hyperion-artifacts-tab-files')!.getAttribute('aria-controls'));
        });

        it('does not move a reader off the tab they are on when a later artifact arrives', () => {
            const host = render({ running: true, files: [change('solution', 'solution/A.java')] });
            const filesPanel = activePanel(host);

            set({ specDocument: '# Spec', savedProblemStatement: '# Statement' });

            expect(activePanel(fixture.nativeElement)).toBe(filesPanel);
        });

        it('keeps a tab the instructor picked themselves', () => {
            const host = render({ terminal: true, savedProblemStatement: '# Statement' });
            query(host, 'hyperion-artifacts-tab-files')!.click();
            fixture.detectChanges();

            const filesPanel = activePanel(fixture.nativeElement);
            set({ specDocument: '# Spec' });

            expect(activePanel(fixture.nativeElement)).toBe(filesPanel);
        });

        it('waits for the retained snapshot before deciding, rather than opening on a provisional answer', () => {
            const pending = new Subject<ExerciseGenerationRetainedArtifacts>();
            getRetained.mockReturnValue(pending);
            const host = render({ exerciseId: EXERCISE_ID, terminal: true });
            // Files is the fallback, so a premature decision would already have landed there and stuck.
            expect(query(host, 'hyperion-artifacts-loading')).not.toBeNull();

            pending.next(retainedArtifacts({ problemStatement: '# Retained statement' }));
            pending.complete();
            fixture.detectChanges();

            expect(activePanel(fixture.nativeElement)).toBe(query(fixture.nativeElement, 'hyperion-artifacts-tab-statement')!.getAttribute('aria-controls'));
        });
    });

    describe('the retained snapshot', () => {
        it('is not asked for while the run is still going, because there can be none', () => {
            render({ exerciseId: EXERCISE_ID, running: true });

            expect(getRetained).not.toHaveBeenCalled();
        });

        it('is not asked for once the run has saved its work, because the exercise is then the truth', () => {
            render({ exerciseId: EXERCISE_ID, terminal: true, savedToExercise: true });

            expect(getRetained).not.toHaveBeenCalled();
        });

        it('is asked for exactly once for a terminal run that kept a draft', () => {
            render({ exerciseId: EXERCISE_ID, terminal: true });
            set({ running: false });
            set({ files: [change('solution', 'solution/A.java')] });

            expect(getRetained).toHaveBeenCalledTimes(1);
            expect(getRetained).toHaveBeenCalledWith(EXERCISE_ID);
        });

        it('shows placeholders in a reserved box while it is in flight, never a blank panel', () => {
            getRetained.mockReturnValue(new Subject());
            const host = render({ exerciseId: EXERCISE_ID, terminal: true });

            const loading = query(host, 'hyperion-artifacts-loading')!;
            expect(loading.getAttribute('aria-busy')).toBe('true');
            expect(loading.querySelector('.sr-only')!.textContent).toContain('artifacts.loading');
            expect(loading.querySelector('tum-ui-skeleton')).not.toBeNull();
        });

        it('treats a 404 as "this run kept nothing" rather than as a failure', () => {
            getRetained.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            const host = render({ exerciseId: EXERCISE_ID, terminal: true });

            expect(query(host, 'hyperion-artifacts-load-failed')).toBeNull();
            expect(query(host, 'hyperion-artifacts-statement-empty')).not.toBeNull();
        });

        it('never swallows a real failure, and keeps the tabs selectable while it says so', () => {
            getRetained.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const host = render({ exerciseId: EXERCISE_ID, terminal: true });

            expect(query(host, 'hyperion-artifacts-load-failed')).not.toBeNull();
            expect(query(host, 'hyperion-artifacts-tab-spec')!.hasAttribute('disabled')).toBe(false);
        });

        it('retries only when the instructor asks, and succeeds on the second attempt', () => {
            getRetained.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
            const host = render({ exerciseId: EXERCISE_ID, terminal: true });
            expect(getRetained).toHaveBeenCalledTimes(1);

            getRetained.mockReturnValue(of(retainedArtifacts({ problemStatement: '# Recovered' })));
            query(host, 'hyperion-artifacts-retry')!.querySelector('button')!.click();
            fixture.detectChanges();

            expect(getRetained).toHaveBeenCalledTimes(2);
            expect(query(fixture.nativeElement, 'hyperion-artifacts-load-failed')).toBeNull();
            expect(query(fixture.nativeElement, 'hyperion-artifacts-statement')!.textContent).toContain('Recovered');
        });
    });

    describe('what it says it has', () => {
        it('renders the statement and the spec as prose, not as source', () => {
            const host = render({ terminal: true, savedProblemStatement: '## Loan periods', specDocument: '## Design' });

            expect(query(host, 'hyperion-artifacts-statement')!.querySelector('h2')!.textContent).toContain('Loan periods');
            expect(query(host, 'hyperion-artifacts-spec')!.querySelector('h2')!.textContent).toContain('Design');
        });

        it('prefers the exercise’s own statement over a retained draft once the run has saved', () => {
            getRetained.mockReturnValue(of(retainedArtifacts({ problemStatement: '# Draft' })));
            const host = render({ exerciseId: EXERCISE_ID, terminal: true, savedProblemStatement: '# Stored' });

            expect(query(host, 'hyperion-artifacts-statement')!.textContent).toContain('Stored');
            expect(query(host, 'hyperion-artifacts-statement')!.textContent).not.toContain('Draft');
        });

        it('changes the reassurance under the heading the moment the run writes into the exercise', () => {
            const host = render({ terminal: true });
            expect(host.textContent).toContain('artifacts.notSavedHint');

            set({ savedToExercise: true });
            expect((fixture.nativeElement as HTMLElement).textContent).toContain('artifacts.savedHint');
        });

        it('gives an empty file list two different sentences before and after the run ends', () => {
            const host = render({ running: true });
            expect(host.textContent).toContain('artifacts.filesPending');

            set({ running: false, terminal: true });
            expect((fixture.nativeElement as HTMLElement).textContent).toContain('artifacts.filesNone');
        });

        it('says what fills an empty markdown tab while the run can still fill it', () => {
            const host = render({ running: true });

            expect(query(host, 'hyperion-artifacts-statement-empty')!.textContent).toContain('artifacts.statementPending');
            expect(query(host, 'hyperion-artifacts-statement-empty')!.textContent).toContain('artifacts.statementPendingHint');
            expect(query(host, 'hyperion-artifacts-spec-empty')!.textContent).toContain('artifacts.specPendingHint');
        });

        it('stops promising an empty tab will fill once the run has ended', () => {
            const host = render({ terminal: true });

            expect(query(host, 'hyperion-artifacts-statement-empty')!.textContent).toContain('artifacts.statementNone');
            expect(query(host, 'hyperion-artifacts-statement-empty')!.textContent).toContain('artifacts.notKeptHint');
            expect(query(host, 'hyperion-artifacts-spec-empty')!.textContent).toContain('artifacts.specNone');
            expect(host.textContent).not.toContain('artifacts.statementPending');
        });
    });

    describe('the file pane', () => {
        it('shows the contents of a file the instructor picks', () => {
            getRetained.mockReturnValue(of(retainedArtifacts({ files: [{ repo: 'solution', path: 'src/A.java', content: 'class A {}' }] })));
            const host = render({ exerciseId: EXERCISE_ID, terminal: true, files: [change('solution', 'solution/src/A.java')] });
            query(host, 'hyperion-artifacts-tab-files')!.click();
            fixture.detectChanges();

            query(fixture.nativeElement, 'hyperion-file-row')!.click();
            fixture.detectChanges();

            expect(query(fixture.nativeElement, 'hyperion-file-content-text')!.textContent).toContain('class A {}');
        });

        it('explains rather than blanks when the run is still going and no endpoint serves a file mid-run', () => {
            const host = render({ running: true, files: [change('solution', 'solution/src/A.java')] });
            query(host, 'hyperion-file-row')!.click();
            fixture.detectChanges();

            expect(query(fixture.nativeElement, 'hyperion-file-content-explanation')!.textContent).toContain('content.pendingRunTitle');
        });

        it('sends a reader to the repository for a run that saved its work', () => {
            const host = render({ terminal: true, savedToExercise: true, files: [change('tests', 'tests/src/ATest.java')] });
            query(host, 'hyperion-artifacts-tab-files')!.click();
            fixture.detectChanges();
            query(fixture.nativeElement, 'hyperion-file-row')!.click();
            fixture.detectChanges();

            expect(query(fixture.nativeElement, 'hyperion-file-content-explanation')!.textContent).toContain('content.savedTitle');
        });

        it('keeps the selected file when the instructor leaves the tab and comes back', () => {
            const host = render({ terminal: true, savedToExercise: true, files: [change('solution', 'solution/src/A.java')] });
            query(host, 'hyperion-artifacts-tab-files')!.click();
            fixture.detectChanges();
            query(fixture.nativeElement, 'hyperion-file-row')!.click();
            fixture.detectChanges();

            query(fixture.nativeElement, 'hyperion-artifacts-tab-spec')!.click();
            fixture.detectChanges();
            query(fixture.nativeElement, 'hyperion-artifacts-tab-files')!.click();
            fixture.detectChanges();

            expect(query(fixture.nativeElement, 'hyperion-file-content-path')!.textContent).toContain('A.java');
        });
    });

    describe('the link into the code editor', () => {
        function openFirstFile(host: HTMLElement): HTMLAnchorElement | null {
            query(host, 'hyperion-artifacts-tab-files')!.click();
            fixture.detectChanges();
            query(fixture.nativeElement, 'hyperion-file-row')!.click();
            fixture.detectChanges();
            return query(fixture.nativeElement, 'hyperion-file-content-open-editor') as HTMLAnchorElement | null;
        }

        it('addresses the test repository by name', () => {
            const host = render({ exerciseId: EXERCISE_ID, exercise: exercise(), terminal: true, savedToExercise: true, files: [change('tests', 'tests/src/ATest.java')] });

            expect(openFirstFile(host)!.getAttribute('href')).toBe('/course-management/7/programming-exercises/42/code-editor/TESTS/test');
        });

        it('addresses the solution repository by its participation', () => {
            const host = render({ exerciseId: EXERCISE_ID, exercise: exercise(), terminal: true, savedToExercise: true, files: [change('solution', 'solution/src/A.java')] });

            expect(openFirstFile(host)!.getAttribute('href')).toBe('/course-management/7/programming-exercises/42/code-editor/SOLUTION/11');
        });

        it('renders no link at all rather than a broken one when the participation is not loaded', () => {
            const host = render({
                exerciseId: EXERCISE_ID,
                exercise: exercise({ solutionParticipation: undefined }),
                terminal: true,
                savedToExercise: true,
                files: [change('solution', 'solution/src/A.java')],
            });

            expect(openFirstFile(host)).toBeNull();
        });

        it('renders no link when the host does not know the exercise at all', () => {
            const host = render({ exerciseId: EXERCISE_ID, terminal: true, savedToExercise: true, files: [change('solution', 'solution/src/A.java')] });

            expect(openFirstFile(host)).toBeNull();
        });

        it('renders no link for a file that belongs to no repository the editor can open', () => {
            const host = render({ exerciseId: EXERCISE_ID, exercise: exercise(), terminal: true, savedToExercise: true, files: [change('other', 'problem-statement.md')] });

            expect(openFirstFile(host)).toBeNull();
        });
    });
});
