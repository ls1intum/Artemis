import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { GitDiffFileComponent } from 'app/programming/shared/git-diff-report/git-diff-file/git-diff-file.component';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { DiffInformation, FileStatus } from 'app/programming/shared/utils/diff.utils';
import { TranslateModule, TranslateService, TranslateStore } from '@ngx-translate/core';

const mockDiffInformations: DiffInformation[] = [
    {
        title: 'modified-file.java',
        modifiedPath: 'src/main/java/modified-file.java',
        originalPath: 'src/main/java/modified-file.java',
        modifiedFileContent: 'public class ModifiedFile { private String name; public void setName(String name) { this.name = name; } public String getName() { return name; } }',
        originalFileContent: 'public class ModifiedFile { private String name; public void setName(String name) { this.name = name; } }',
        diffReady: true,
        fileStatus: FileStatus.UNCHANGED,
        lineChange: { addedLineCount: 1, removedLineCount: 0 },
    },
    {
        title: 'old-name.java → new-name.java',
        modifiedPath: 'src/main/java/new-name.java',
        originalPath: 'src/main/java/old-name.java',
        modifiedFileContent: 'public class NewName { private String name; public void setName(String name) { this.name = name; } }',
        originalFileContent: 'public class OldName { private String name; public void setName(String name) { this.name = name; } }',
        diffReady: true,
        fileStatus: FileStatus.RENAMED,
        lineChange: { addedLineCount: 1, removedLineCount: 1 },
    },
    {
        title: 'new-file.java',
        modifiedPath: 'src/main/java/new-file.java',
        originalPath: '',
        modifiedFileContent: 'public class NewFile { private String name; public void setName(String name) { this.name = name; } }',
        originalFileContent: '',
        diffReady: true,
        fileStatus: FileStatus.CREATED,
        lineChange: { addedLineCount: 1, removedLineCount: 0 },
    },
    {
        title: 'deleted-file.java',
        modifiedPath: '',
        originalPath: 'src/main/java/deleted-file.java',
        modifiedFileContent: '',
        originalFileContent: 'public class DeletedFile { private String name; public void setName(String name) { this.name = name; } }',
        diffReady: true,
        fileStatus: FileStatus.DELETED,
        lineChange: { addedLineCount: 0, removedLineCount: 1 },
    },
    {
        title: 'unchanged-file.java',
        modifiedPath: 'src/main/java/unchanged-file.java',
        originalPath: 'src/main/java/unchanged-file.java',
        modifiedFileContent: 'public class UnchangedFile { private String name; public void setName(String name) { this.name = name; } }',
        originalFileContent: 'public class UnchangedFile { private String name; public void setName(String name) { this.name = name; } }',
        diffReady: true,
        fileStatus: FileStatus.UNCHANGED,
        lineChange: { addedLineCount: 0, removedLineCount: 0 },
    },
    {
        title: 'too-large-file.java',
        modifiedPath: 'src/main/java/too-large-file.java',
        originalPath: 'src/main/java/too-large-file.java',
        modifiedFileContent: 'line'.repeat(10),
        originalFileContent: 'LINE'.repeat(10),
        diffReady: true,
        fileStatus: FileStatus.UNCHANGED,
        lineChange: { addedLineCount: 25, removedLineCount: 30, fileTooLarge: true },
    },
];

describe('GitDiffFileComponent', () => {
    let comp: GitDiffFileComponent;
    let fixture: ComponentFixture<GitDiffFileComponent>;

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch (error) {
            // The environment is already initialized when running through the Angular CLI test harness.
        }
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [GitDiffFileComponent, TranslateModule.forRoot()],
            providers: [{ provide: ThemeService, useClass: MockThemeService }, TranslateStore],
        }).compileComponents();
        // Required because Monaco uses the ResizeObserver for the diff editor.
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(GitDiffFileComponent);
        comp = fixture.componentInstance;

        const translateService = TestBed.inject(TranslateService);
        translateService.setTranslation(
            'en',
            {
                artemisApp: {
                    repository: {
                        commitHistory: {
                            commitDetails: {
                                fileUnchanged: 'No changes in the file content',
                                fileTooLarge: 'Changes are too large to display. Showing approximate summary.',
                            },
                        },
                    },
                },
            },
            true,
        );
        translateService.use('en');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each(mockDiffInformations)('should handle $fileStatus file correctly', (diffInfo: DiffInformation) => {
        const setFileContentsStub = jest.fn();
        jest.spyOn(comp, 'monacoDiffEditor').mockReturnValue({ setFileContents: setFileContentsStub } as unknown as MonacoDiffEditorComponent);
        fixture.componentRef.setInput('diffInformation', diffInfo);
        fixture.detectChanges();

        expect(setFileContentsStub).toHaveBeenCalledWith(diffInfo.originalFileContent, diffInfo.modifiedFileContent, diffInfo.originalPath, diffInfo.modifiedPath);
        expect(comp.fileUnchanged()).toBe(diffInfo.originalFileContent === diffInfo.modifiedFileContent);
    });

    it('should display an informative message when a diff is too large', () => {
        const diffInfo = mockDiffInformations.find((info) => info.lineChange?.fileTooLarge) as DiffInformation;
        expect(diffInfo).toBeDefined();
        const setFileContentsStub = jest.fn();
        jest.spyOn(comp, 'monacoDiffEditor').mockReturnValue({ setFileContents: setFileContentsStub } as unknown as MonacoDiffEditorComponent);

        fixture.componentRef.setInput('diffInformation', diffInfo);
        fixture.detectChanges();

        const renderedText = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(renderedText).toContain('Changes are too large to display. Showing approximate summary.');
        expect(setFileContentsStub).toHaveBeenCalled();
    });
});
