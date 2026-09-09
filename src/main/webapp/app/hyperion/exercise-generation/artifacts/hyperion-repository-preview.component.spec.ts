import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionRepositoryPreviewComponent } from 'app/hyperion/exercise-generation/artifacts/hyperion-repository-preview.component';
import { artifactFiles } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { DomainChange, DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';

const file = (path = 'src/Box.java') => artifactFiles([{ type: 'FILE_CHANGE', repo: 'solution', path, action: 'write', turn: 1, timestamp: '2026-09-09T12:00:00Z' }])[0];
const exercise = { id: 42, templateParticipation: { id: 11 }, solutionParticipation: { id: 12 } } as ProgrammingExercise;

describe('HyperionRepositoryPreviewComponent', () => {
    let fixture: ComponentFixture<HyperionRepositoryPreviewComponent>;
    let getFile: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        getFile = vi.fn().mockReturnValue(of({ fileContent: 'public class Box {}' }));
        await TestBed.configureTestingModule({
            imports: [HyperionRepositoryPreviewComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }, { provide: CodeEditorRepositoryFileService, useValue: { getFile } }],
        })
            .overrideComponent(HyperionRepositoryPreviewComponent, { remove: { imports: [GitDiffFileComponent] }, add: { imports: [MockComponent(GitDiffFileComponent)] } })
            .compileComponents();
        fixture = TestBed.createComponent(HyperionRepositoryPreviewComponent);
        fixture.componentRef.setInput('file', file());
        fixture.componentRef.setInput('exercise', exercise);
    });

    it('reads only the selected file using an explicit repository without changing editor state', () => {
        fixture.detectChanges();
        expect(getFile).toHaveBeenCalledExactlyOnceWith('src/Box.java', [DomainType.PARTICIPATION, exercise.solutionParticipation]);
        expect(fixture.componentInstance['preview']()).toEqual({ kind: 'file', state: { kind: 'text', content: 'public class Box {}', lineCount: 1 } });
    });

    it('compares starter to solution and treats only an actual 404 as a missing student-created type', () => {
        getFile.mockImplementation((_path: string, domain: DomainChange) =>
            domain[1].id === 11 ? throwError(() => new HttpErrorResponse({ status: 404 })) : of({ fileContent: 'public class Box {}' }),
        );
        fixture.componentInstance['compare'].set(true);
        fixture.detectChanges();
        const view = fixture.componentInstance['preview']();
        expect(view.kind).toBe('comparison');
        if (view.kind === 'comparison') {
            expect(view.templateMissing).toBe(true);
            expect(view.solutionMissing).toBe(false);
            expect(view.diff.originalFileContent).toBe('');
            expect(view.diff.modifiedFileContent).toBe('public class Box {}');
            expect(view.diff.originalPath).toBe('template/src/Box.java');
        }
    });

    it('never turns authorization or network failures into an empty side of the diff', () => {
        getFile.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
        fixture.componentInstance['compare'].set(true);
        fixture.detectChanges();
        expect(fixture.componentInstance['preview']()).toEqual({ kind: 'file', state: { kind: 'failed' } });
        getFile.mockReturnValue(of({ fileContent: 'recovered' }));
        fixture.componentInstance['retryRead']();
        fixture.detectChanges();
        expect(fixture.componentInstance['preview']().kind).toBe('comparison');
    });

    it('cancels old reads and resets comparison when the selected file changes', () => {
        const first = new Subject<{ fileContent: string }>();
        getFile.mockReturnValue(first);
        fixture.detectChanges();
        fixture.componentInstance['compare'].set(true);
        fixture.detectChanges();
        getFile.mockReturnValue(of({ fileContent: 'new file' }));
        fixture.componentRef.setInput('file', file('src/New.java'));
        fixture.detectChanges();
        first.next({ fileContent: 'stale' });
        first.complete();
        expect(fixture.componentInstance['compare']()).toBe(false);
        expect(fixture.componentInstance['preview']()).toEqual({ kind: 'file', state: { kind: 'text', content: 'new file', lineCount: 1 } });
    });
});
