import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { MonacoDiffEditorComponent } from '../../../../../../main/webapp/app/shared/monaco-editor/monaco-diff-editor.component';

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
            imports: [ArtemisTestModule, MonacoDiffEditorComponent],
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

    it.each([
        getDiffEntryWithPaths('same file', 'same file'),
        getDiffEntryWithPaths('old file', 'renamed file'),
        getDiffEntryWithPaths('deleted file', undefined),
        getDiffEntryWithPaths(undefined, 'created file'),
    ])('should infer file paths from the diff entries', (entry) => {
        comp.diffEntries = [entry];
        fixture.detectChanges();
        expect(comp.modifiedFilePath).toBe(entry.filePath);
        expect(comp.originalFilePath).toBe(entry.previousFilePath);
    });

    it('should initialize the content of the diff editor', () => {
        const fileName = 'some-changed-file.java';
        const originalContent = 'some file content';
        const modifiedContent = 'some changed file content';
        const setFileContentsStub = jest.spyOn(comp.monacoDiffEditor, 'setFileContents').mockImplementation();
        const diffEntry = getDiffEntryWithPaths(fileName, fileName);
        comp.originalFileContent = originalContent;
        comp.modifiedFileContent = modifiedContent;
        comp.diffEntries = [diffEntry];
        fixture.detectChanges();
        expect(setFileContentsStub).toHaveBeenCalledExactlyOnceWith(originalContent, fileName, modifiedContent, fileName);
    });
});
