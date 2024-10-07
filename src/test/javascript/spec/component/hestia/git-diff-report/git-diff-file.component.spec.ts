import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';
import { MonacoDiffEditorComponent } from '../../../../../../main/webapp/app/shared/monaco-editor/monaco-diff-editor.component';
import { MockComponent } from 'ng-mocks';

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
            imports: [ArtemisTestModule],
            declarations: [MockComponent(MonacoDiffEditorComponent)],
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

    it('should initialize the content of the diff editor', () => {
        const fileName = 'some-changed-file.java';
        const originalContent = 'some file content';
        const modifiedContent = 'some changed file content';
        const setFileContentsStub = jest.fn();
        jest.spyOn(comp, 'monacoDiffEditor').mockReturnValue({ setFileContents: setFileContentsStub } as unknown as MonacoDiffEditorComponent);
        const diffEntry = getDiffEntryWithPaths(fileName, fileName);
        fixture.componentRef.setInput('diffEntries', [diffEntry]);
        fixture.componentRef.setInput('originalFileContent', originalContent);
        fixture.componentRef.setInput('originalFilePath', fileName);
        fixture.componentRef.setInput('modifiedFileContent', modifiedContent);
        fixture.componentRef.setInput('modifiedFilePath', fileName);
        jest.spyOn(comp.monacoDiffEditor(), 'setFileContents').mockImplementation();
        fixture.detectChanges();
        expect(setFileContentsStub).toHaveBeenCalledExactlyOnceWith(originalContent, fileName, modifiedContent, fileName);
    });
});
