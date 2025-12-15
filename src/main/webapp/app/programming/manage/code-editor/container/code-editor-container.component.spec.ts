import { TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { CodeEditorContainerComponent, CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import {
    CommitState,
    CreateFileChange,
    DeleteFileChange,
    EditorState,
    FileBadgeType,
    FileType,
    RenameFileChange,
    RepositoryType,
} from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseEditorFileChangeType } from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ConnectionError } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

class MockFileService {
    updateFileReferences = jest.fn((refs) => refs);
    updateFileReference = jest.fn((file) => file);
}

describe('CodeEditorContainerComponent', () => {
    let component: CodeEditorContainerComponent;
    let alertService: AlertService;
    let fileService: MockFileService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeEditorContainerComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
                { provide: AlertService, useValue: { error: jest.fn() } as any },
                { provide: CodeEditorFileService, useClass: MockFileService },
            ],
        })
            .overrideComponent(CodeEditorContainerComponent, {
                set: { template: '' },
            })
            .compileComponents();

        const fixture = TestBed.createComponent(CodeEditorContainerComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fileService = TestBed.inject(CodeEditorFileService) as unknown as MockFileService;
        component.monacoEditor = {
            onFileChange: jest.fn(),
            storeAnnotations: jest.fn(),
            getText: jest.fn().mockReturnValue('content'),
            getNumberOfLines: jest.fn().mockReturnValue(3),
            highlightLines: jest.fn(),
            getFileContent: jest.fn(),
            applyRemoteFileContent: jest.fn(),
        } as any;
        component.grid = { toggleCollapse: jest.fn() } as any;
    });

    afterEach(() => jest.clearAllMocks());

    it('should initialize defaults', () => {
        expect(component.selectedRepository).toBe(RepositoryType.TEMPLATE);
        expect(component.editorState).toBe(EditorState.CLEAN);
        expect(component.commitState).toBe(CommitState.UNDEFINED);
        expect(component.unsavedFiles).toEqual({});
        expect(component.isProblemStatementVisible()).toBeTrue();
    });

    it('should update file badges when feedback suggestions change', () => {
        component.feedbackSuggestions = [
            { reference: 'file:src/main/App.java_line:3' } as Feedback,
            { reference: 'file:src/main/App.java_line:10' } as Feedback,
            { reference: 'file:src/Other.java_line:5' } as Feedback,
        ];

        component.ngOnChanges({ feedbackSuggestions: new SimpleChange([], component.feedbackSuggestions, true) });

        expect(Object.keys(component.fileBadges)).toEqual(expect.arrayContaining(['src/main/App.java', 'src/Other.java']));
        expect(component.fileBadges['src/main/App.java'][0].type).toBe(FileBadgeType.FEEDBACK_SUGGESTION);
        expect(component.fileBadges['src/main/App.java'][0].count).toBe(2);
    });

    it('should adjust editor and commit states based on unsaved files', () => {
        component.unsavedFiles = { 'src/File.java': 'content' };

        expect(component.editorState).toBe(EditorState.UNSAVED_CHANGES);
        expect(component.commitState).toBe(CommitState.UNCOMMITTED_CHANGES);

        component.editorState = EditorState.SAVING;
        component.unsavedFiles = {};
        expect(component.editorState).toBe(EditorState.CLEAN);
        expect(component.commitState).toBe(CommitState.UNCOMMITTED_CHANGES);

        component.editorState = EditorState.UNSAVED_CHANGES;
        component.unsavedFiles = {};
        expect(component.editorState).toBe(EditorState.CLEAN);
    });

    it('should handle new file creation', () => {
        const onFileChanged = jest.fn();
        component.onFileChanged.subscribe(onFileChanged);
        const syncSpy = jest.fn();
        component.fileOperationSync.subscribe(syncSpy);

        component.onFileChange([[], new CreateFileChange(FileType.FILE, 'src/main/App.java')]);

        expect(component.selectedFile).toBe('src/main/App.java');
        expect(component.commitState).toBe(CommitState.UNCOMMITTED_CHANGES);
        expect(onFileChanged).toHaveBeenCalled();
        expect((component.monacoEditor as any).onFileChange).toHaveBeenCalled();
        expect(syncSpy).toHaveBeenCalledWith({
            type: ProgrammingExerciseEditorFileChangeType.CREATE,
            fileName: 'src/main/App.java',
            content: '',
            fileType: FileType.FILE,
        });
    });

    it('should update references on rename and mark unsaved state', () => {
        component.unsavedFiles = { 'old/File.java': 'x' };
        component.selectedFile = 'old/File.java';
        const onFileChanged = jest.fn();
        component.onFileChanged.subscribe(onFileChanged);
        fileService.updateFileReferences.mockReturnValue({ 'new/File.java': 'x' });
        fileService.updateFileReference.mockReturnValue('new/File.java');

        component.monacoEditor = { ...component.monacoEditor, getFileContent: jest.fn().mockReturnValue('renamed') } as any;
        const syncSpy = jest.fn();
        component.fileOperationSync.subscribe(syncSpy);

        component.onFileChange([[], new RenameFileChange(FileType.FILE, 'old/File.java', 'new/File.java')]);

        expect(fileService.updateFileReferences).toHaveBeenCalled();
        expect(component.unsavedFiles).toEqual({ 'new/File.java': 'x' });
        expect(component.selectedFile).toBe('new/File.java');
        expect(component.commitState).toBe(CommitState.UNCOMMITTED_CHANGES);
        expect(onFileChanged).toHaveBeenCalled();
        expect(syncSpy).toHaveBeenCalledWith({
            type: ProgrammingExerciseEditorFileChangeType.RENAME,
            fileName: 'old/File.java',
            newFileName: 'new/File.java',
            content: 'renamed',
        });
    });

    it('should reset editor state when deletions clear unsaved files', () => {
        component.editorState = EditorState.UNSAVED_CHANGES;
        component.unsavedFiles = { 'old/File.java': 'x' };
        fileService.updateFileReferences.mockReturnValue({});
        const syncSpy = jest.fn();
        component.fileOperationSync.subscribe(syncSpy);

        component.onFileChange([[], new DeleteFileChange(FileType.FILE, 'old/File.java')]);

        expect(component.unsavedFiles).toEqual({});
        expect(component.editorState).toBe(EditorState.CLEAN);
        expect(syncSpy).toHaveBeenCalledWith({ type: ProgrammingExerciseEditorFileChangeType.DELETE, fileName: 'old/File.java' });
    });

    it('should update unsaved files and notify on content change', () => {
        const onFileChanged = jest.fn();
        component.onFileChanged.subscribe(onFileChanged);
        const syncSpy = jest.fn();
        component.fileOperationSync.subscribe(syncSpy);

        component.onFileContentChange({ fileName: 'src/main/App.java', text: 'new content' });

        expect(component.unsavedFiles['src/main/App.java']).toBe('new content');
        expect(onFileChanged).toHaveBeenCalled();
        expect(syncSpy).toHaveBeenCalledWith({
            type: ProgrammingExerciseEditorFileChangeType.CONTENT,
            fileName: 'src/main/App.java',
            content: 'new content',
        });
    });

    it('does not emit sync updates while applying remote content', () => {
        const syncSpy = jest.fn();
        component.fileOperationSync.subscribe(syncSpy);

        (component as any).isApplyingRemoteFileUpdate = true;
        component.onFileContentChange({ fileName: 'src/main/App.java', text: 'new content' });

        expect(syncSpy).not.toHaveBeenCalled();
    });

    it('applies remote content without triggering synchronization', () => {
        const syncSpy = jest.fn();
        component.fileOperationSync.subscribe(syncSpy);

        component.applyRemoteFileContent('src/main/App.java', 'remote');

        expect((component.monacoEditor as any).applyRemoteFileContent).toHaveBeenCalledWith('src/main/App.java', 'remote');
        expect(component.unsavedFiles['src/main/App.java']).toBe('remote');
        expect(syncSpy).not.toHaveBeenCalled();
    });

    it('resets remote update flag when applying remote content fails', () => {
        const error = new Error('apply failed');
        (component.monacoEditor as any).applyRemoteFileContent.mockImplementation(() => {
            throw error;
        });

        expect(() => component.applyRemoteFileContent('src/main/App.java', 'remote')).toThrow(error);
        expect((component as any).isApplyingRemoteFileUpdate).toBeFalse();
    });

    it('emits file load events', () => {
        const loadSpy = jest.fn();
        component.onFileLoad.subscribe(loadSpy);

        component.fileLoad('src/main/App.java');

        expect(loadSpy).toHaveBeenCalledWith('src/main/App.java');
    });

    it('should clear unsaved files after refresh', () => {
        component.unsavedFiles = { 'src/main/App.java': 'x' };

        component.onRefreshFiles();

        expect(component.unsavedFiles).toEqual({});
    });

    it('should keep only files with errors after saving and show alert', () => {
        component.unsavedFiles = { 'a.java': 'x', 'b.java': 'y' };

        component.onSavedFiles({ 'a.java': undefined, 'b.java': 'error' });

        expect(component.unsavedFiles).toEqual({ 'b.java': 'y' });
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.editor.errors.saveFailed', { connectionIssue: '' });
        expect((component.monacoEditor as any).storeAnnotations).toHaveBeenCalledWith(['a.java']);
    });

    it('should forward annotations and compute error files', () => {
        component.onAnnotations([{ fileName: 'A.java', type: 'warning' } as any, { fileName: 'B.java', type: 'error' } as any, { fileName: 'B.java', type: 'error' } as any]);

        expect(component.errorFiles).toEqual(['B.java']);
        expect(component.annotations).toHaveLength(3);
    });

    it('should propagate error messages and include connection details', () => {
        const translate = TestBed.inject(TranslateService);
        const instantSpy = jest.spyOn(translate, 'instant');

        component.onError(`test${ConnectionError.message}`);

        expect(instantSpy).toHaveBeenCalledWith(`artemisApp.editor.errors.${ConnectionError.message}`);
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.editor.errors.test', {
            connectionIssue: `artemisApp.editor.errors.${ConnectionError.message}`,
        });
    });

    it('should delegate monaco helpers', () => {
        expect(component.getText()).toBe('content');
        expect(component.getNumberOfLines()).toBe(3);

        component.highlightLines(1, 3);
        expect((component.monacoEditor as any).highlightLines).toHaveBeenCalledWith(1, 3);
    });

    it('should toggle collapsable elements', () => {
        const event = { type: 'click' } as any;

        component.onToggleCollapse(event, CollapsableCodeEditorElement.BuildOutput);

        expect(component.grid.toggleCollapse).toHaveBeenCalledWith(event, CollapsableCodeEditorElement.BuildOutput);
    });

    it('should expose feedbacks for submission when inline feedback is enabled', () => {
        const feedback = { id: 1 } as Feedback;
        component.participation = {
            submissions: [{ results: [{ feedbacks: [feedback] } as Result] } as Submission],
        } as Participation;

        expect(component.feedbackForSubmission()).toEqual([feedback]);

        component.showInlineFeedback = false;
        expect(component.feedbackForSubmission()).toEqual([]);
    });

    it('should guard deactivation when unsaved files exist', () => {
        const event = { preventDefault: jest.fn() } as any;

        component.unsavedFiles = { 'src/main/App.java': 'x' };
        expect(component.canDeactivate()).toBeFalse();
        expect(component.unloadNotification(event)).toBe('pendingChanges');
        expect(event.preventDefault).toHaveBeenCalled();

        component.unsavedFiles = {};
        event.preventDefault.mockClear();
        expect(component.unloadNotification(event)).toBeTrue();
        expect(event.preventDefault).not.toHaveBeenCalled();
    });
});
