import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionFileContentComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-file-content.component';
import { HyperionArtifactContentState, HyperionArtifactFile } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';

const EDITOR_LINK = ['/course-management', 7, 'programming-exercises', 42, 'code-editor', 'SOLUTION', 11] as const;

function file(overrides: Partial<HyperionArtifactFile> = {}): HyperionArtifactFile {
    return {
        key: 'solution\0src/de/tum/Loan.java',
        repo: 'solution',
        path: 'src/de/tum/Loan.java',
        directory: 'src/de/tum/',
        name: 'Loan.java',
        action: 'write',
        turn: 3,
        mostRecent: false,
        ...overrides,
    };
}

describe('HyperionFileContentComponent', () => {
    let fixture: ComponentFixture<HyperionFileContentComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HyperionFileContentComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionFileContentComponent);
    });

    function show(selected: HyperionArtifactFile | undefined, state: HyperionArtifactContentState | undefined, editorLink?: readonly (string | number)[]): HTMLElement {
        fixture.componentRef.setInput('file', selected);
        fixture.componentRef.setInput('state', state);
        fixture.componentRef.setInput('editorLink', editorLink);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    function query(host: HTMLElement, testId: string): HTMLElement | null {
        return host.querySelector(`[data-testid="${testId}"]`);
    }

    it('invites a selection rather than reporting on nothing when no file is picked', () => {
        const host = show(undefined, undefined);

        expect(query(host, 'hyperion-file-content-none')).not.toBeNull();
        expect(host.textContent).toContain('content.selectTitle');
        expect(host.textContent).toContain('content.selectHint');
    });

    it('still shows nothing selected when a state arrives without a file', () => {
        expect(query(show(undefined, { kind: 'text', content: 'x', lineCount: 1 }), 'hyperion-file-content-none')).not.toBeNull();
    });

    it('shows the file’s text with its line count and its action', () => {
        const host = show(file(), { kind: 'text', content: 'class Loan {}\n', lineCount: 2 });

        expect(query(host, 'hyperion-file-content-text')!.textContent).toContain('class Loan {}');
        expect(host.textContent).toContain('content.lines');
        expect(host.textContent).toContain('action.write');
    });

    it('clips the directory but never the file name, because the name is the identity', () => {
        const path = query(show(file(), { kind: 'text', content: 'x', lineCount: 1 }), 'hyperion-file-content-path')!;

        expect(path.children[0].className).toContain('truncate');
        expect(path.children[0].textContent).toBe('src/de/tum/');
        expect(path.children[1].className).not.toContain('truncate');
        expect(path.children[1].textContent).toBe('Loan.java');
    });

    it('reserves the box and announces busy while the retained snapshot is in flight, without a shimmer', () => {
        const host = show(file(), { kind: 'loading' });

        const loading = query(host, 'hyperion-file-content-loading')!;
        expect(loading.getAttribute('aria-busy')).toBe('true');
        expect(loading.querySelector('.sr-only')!.textContent).toContain('content.loading');
        expect(loading.querySelector('tum-ui-skeleton')).not.toBeNull();
        expect(query(host, 'hyperion-file-content-text')).toBeNull();
    });

    const EXPLAINED: [state: HyperionArtifactContentState, copy: string][] = [
        [{ kind: 'empty' }, 'content.emptyFileTitle'],
        [{ kind: 'deleted' }, 'content.deletedTitle'],
        [{ kind: 'failed' }, 'content.failedTitle'],
        [{ kind: 'pendingRun' }, 'content.pendingRunTitle'],
        [{ kind: 'savedToExercise' }, 'content.savedTitle'],
        [{ kind: 'notRetained' }, 'content.notRetainedTitle'],
    ];

    it.each(EXPLAINED)('never renders a blank pane: %o is explained on screen', (state, copy) => {
        const host = show(file(), state);

        expect(query(host, 'hyperion-file-content-explanation')).not.toBeNull();
        expect(host.textContent).toContain(copy);
        expect(query(host, 'hyperion-file-content-text')).toBeNull();
    });

    it('publishes the state on the host so a stylesheet or a test can select on it', () => {
        expect(show(file(), { kind: 'pendingRun' }).querySelector('[data-slot="file-content"]')!.getAttribute('data-state')).toBe('pendingRun');
    });

    it('offers a retry only for the state whose owner can actually retry, and emits it', () => {
        const retried = vi.fn();
        fixture.componentInstance.retryRequested.subscribe(retried);

        expect(query(show(file(), { kind: 'notRetained' }), 'hyperion-file-content-retry')).toBeNull();

        const retry = query(show(file(), { kind: 'failed' }), 'hyperion-file-content-retry');
        expect(retry).not.toBeNull();
        retry!.querySelector('button')!.click();

        expect(retried).toHaveBeenCalledTimes(1);
    });

    it('does not render a dead affordance when this viewer cannot reach the editor', () => {
        expect(query(show(file(), { kind: 'savedToExercise' }, undefined), 'hyperion-file-content-open-editor')).toBeNull();
    });

    it('lands "Open in code editor" on the file being read, not at the top of the repository', () => {
        const host = show(file(), { kind: 'savedToExercise' }, EDITOR_LINK);

        const link = query(host, 'hyperion-file-content-open-editor') as HTMLAnchorElement;
        expect(link).not.toBeNull();
        expect(link.getAttribute('href')).toBe('/course-management/7/programming-exercises/42/code-editor/SOLUTION/11');
        expect(fixture.componentInstance['editorNavigationState']()).toEqual({ openGenerationActivity: true, openGenerationFilePath: 'src/de/tum/Loan.java' });
    });

    it('asks the editor for the AI panel but not for a file that is no longer there', () => {
        show(file({ action: 'delete' }), { kind: 'deleted' }, EDITOR_LINK);

        expect(fixture.componentInstance['editorNavigationState']()).toEqual({ openGenerationActivity: true });
    });

    it('takes the docked-panel density without becoming a second component', () => {
        fixture.componentRef.setInput('file', file());
        fixture.componentRef.setInput('state', { kind: 'text', content: 'x', lineCount: 1 });
        fixture.componentRef.setInput('density', 'compact');
        fixture.detectChanges();

        expect((fixture.nativeElement as HTMLElement).querySelector('pre')!.className).toContain('hyperion-file-content-compact');
    });

    it('drops the action tag for a file only the retained snapshot knows about', () => {
        const host = show(file({ action: undefined }), { kind: 'text', content: 'x', lineCount: 1 });

        expect(host.querySelector('tum-ui-tag')).toBeNull();
    });
});
