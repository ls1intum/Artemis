import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';

function getDiffEntryWithPaths(previousFilePath?: string, filePath?: string) {
    return {
        previousFilePath,
        filePath,
    };
}

describe('GitDiffFileComponent', () => {
    let comp: GitDiffFileComponent;
    let fixture: ComponentFixture<GitDiffFileComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [GitDiffFileComponent],
            providers: [],
        }).compileComponents();
        // Required because Monaco uses the ResizeObserver for the diff editor.
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(GitDiffFileComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([true, false])('should hide unchanged regions only outside of the diff between template and solution', (diffBetweenTemplateAndSolution) => {
        const setUnchangedRegionHidingEnabledSpy = jest.spyOn(comp.monacoDiffEditor, 'setUnchangedRegionHidingEnabled');
        comp.diffEntries = [];
        comp.diffForTemplateAndSolution = diffBetweenTemplateAndSolution;
        fixture.detectChanges();
        expect(setUnchangedRegionHidingEnabledSpy).toHaveBeenCalledExactlyOnceWith(!diffBetweenTemplateAndSolution);
    });

    it.each([
        getDiffEntryWithPaths('same file', 'same file'),
        getDiffEntryWithPaths('old file', 'renamed file'),
        getDiffEntryWithPaths('deleted file', undefined),
        getDiffEntryWithPaths(undefined, 'created file'),
    ])('should correctly infer file paths from the diff entries', (entry) => {
        comp.diffEntries = [entry];
        fixture.detectChanges();
        expect(comp.filePath).toBe(entry.filePath);
        expect(comp.previousFilePath).toBe(entry.previousFilePath);
    });

    it('should correctly initialize the content of the diff editor', () => {
        const fileName = 'some-changed-file.java';
        const originalContent = 'some file content';
        const modifiedContent = 'some changed file content';
        const setFileContentsStub = jest.spyOn(comp.monacoDiffEditor, 'setFileContents').mockImplementation();
        const diffEntry = getDiffEntryWithPaths(fileName, fileName);
        comp.templateFileContent = originalContent;
        comp.solutionFileContent = modifiedContent;
        comp.diffEntries = [diffEntry];
        fixture.detectChanges();
        expect(setFileContentsStub).toHaveBeenCalledExactlyOnceWith(originalContent, fileName, modifiedContent, fileName);
    });
});
