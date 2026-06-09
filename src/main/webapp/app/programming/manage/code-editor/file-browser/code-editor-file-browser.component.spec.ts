import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { Subject, of } from 'rxjs';
import {
    CommitState,
    CreateFileChange,
    DeleteFileChange,
    FileBadge,
    FileBadgeType,
    FileChange,
    FileType,
    GitConflictState,
    PROBLEM_STATEMENT_IDENTIFIER,
} from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorFileBrowserFolderComponent } from 'app/programming/manage/code-editor/file-browser/folder/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/programming/manage/code-editor/file-browser/create-node/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/programming/manage/code-editor/file-browser/file/code-editor-file-browser-file.component';
import { CodeEditorFileBrowserComponent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/programming/shared/code-editor/status/code-editor-status.component';
import { MockCodeEditorRepositoryService } from 'test/helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from 'test/helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorConflictStateService } from 'test/helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorFileBrowserProblemStatementComponent } from 'app/programming/manage/code-editor/file-browser/problem-statement/code-editor-file-browser-problem-statement.component';
import { CodeEditorFileSyncService } from 'app/exercise/synchronization/services/code-editor-file-sync.service';

/**
 * Typed view onto the private `handleProblemStatementVisibility` method and the (publicly typed but
 * occasionally cleared in tests) `repositoryFiles` map so the spec can drive them without a blanket
 * `(comp as any)` cast. `repositoryFiles` is re-declared as optionally `undefined` because one test
 * deliberately resets it to verify the re-initialisation path.
 */
type FileBrowserInternals = Omit<CodeEditorFileBrowserComponent, 'repositoryFiles'> & {
    repositoryFiles: { [fileName: string]: FileType } | undefined;
    handleProblemStatementVisibility?: () => void;
};
const internals = (c: CodeEditorFileBrowserComponent): FileBrowserInternals => c as unknown as FileBrowserInternals;

describe('CodeEditorFileBrowserComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CodeEditorFileBrowserComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let conflictService: CodeEditorConflictStateService;
    let getRepositoryContentStub: ReturnType<typeof vi.spyOn>;
    let getStatusStub: ReturnType<typeof vi.spyOn>;
    let createFileStub: ReturnType<typeof vi.spyOn>;
    let renameFileStub: ReturnType<typeof vi.spyOn>;

    const createFileRoot = '#create_file_root';
    const createFolderRoot = '#create_folder_root';
    const compressTree = '#compress_tree';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
                CodeEditorFileBrowserComponent,
                CodeEditorFileBrowserFileComponent,
                CodeEditorFileBrowserFolderComponent,
                CodeEditorFileBrowserCreateNodeComponent,
                MockComponent(CodeEditorStatusComponent),
                TranslatePipeMock,
                MockComponent(CodeEditorFileBrowserProblemStatementComponent),
            ],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeEditorFileBrowserComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        codeEditorRepositoryFileService = TestBed.inject(CodeEditorRepositoryFileService);
        codeEditorRepositoryService = TestBed.inject(CodeEditorRepositoryService);
        conflictService = TestBed.inject(CodeEditorConflictStateService);
        getStatusStub = vi.spyOn(codeEditorRepositoryService, 'getStatus');
        getRepositoryContentStub = vi.spyOn(codeEditorRepositoryFileService, 'getRepositoryContent');
        createFileStub = vi.spyOn(codeEditorRepositoryFileService, 'createFile').mockReturnValue(of(undefined));
        renameFileStub = vi.spyOn(codeEditorRepositoryFileService, 'renameFile').mockReturnValue(of(undefined));
        let lastCommitState: CommitState | undefined;
        comp.commitStateChange.subscribe((state) => {
            if (state !== lastCommitState) {
                lastCommitState = state;
                fixture.componentRef.setInput('commitState', state);
            }
        });
        let lastSelectedFile: string | undefined;
        comp.selectedFileChange.subscribe((file) => {
            if (file !== lastSelectedFile) {
                lastSelectedFile = file;
                fixture.componentRef.setInput('selectedFile', file);
            }
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('uncompresses the tree when toggling from true → false', () => {
        comp.repositoryFiles = { a: FileType.FILE };
        comp.compressFolders = true;

        const treeNode = { folder: '', file: 'a', children: [], text: 'a', value: 'a' };
        vi.spyOn(comp, 'buildTree').mockReturnValue([treeNode]);
        const transformSpy = vi.spyOn(comp, 'transformTreeToTreeViewItem').mockReturnValue([new TreeViewItem(treeNode)]);

        comp.toggleTreeCompress();

        expect(comp.compressFolders).toBe(false);
        expect(transformSpy).toHaveBeenCalled();
    });

    it('returns [] for getFolderBadges on unknown folder', () => {
        fixture.componentRef.setInput('fileBadges', { 'known/file': [] });
        fixture.detectChanges();
        const result = comp.getFolderBadges({ value: 'unknown', collapsed: true } as TreeViewItem<string>);
        expect(result).toEqual([]);
    });

    it('should NOT open a delete modal for Problem Statement (PS is not deletable)', () => {
        // PS present
        fixture.componentRef.setInput('isProblemStatementVisible', true);
        comp.repositoryFiles = { [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT };
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();

        const item = { value: PROBLEM_STATEMENT_IDENTIFIER, text: PROBLEM_STATEMENT_IDENTIFIER } as TreeViewItem<string>;
        const openModalSpy = vi.spyOn(comp.modalService, 'open');

        // Try to delete PS
        comp.openDeleteFileModal(item);

        // Expect no modal to be opened
        expect(openModalSpy).not.toHaveBeenCalled();
    });

    it('should pass file badges to Problem Statement entry', () => {
        const problemStatementBadges = [new FileBadge(FileBadgeType.REVIEW_COMMENT, 2)];
        fixture.componentRef.setInput('isProblemStatementVisible', true);
        fixture.componentRef.setInput('fileBadges', { [PROBLEM_STATEMENT_IDENTIFIER]: problemStatementBadges });
        comp.repositoryFiles = { [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT };
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();

        const problemStatement = debugElement.query(By.directive(CodeEditorFileBrowserProblemStatementComponent));
        expect(problemStatement).toBeTruthy();
        expect(problemStatement.componentInstance.badges()).toEqual(problemStatementBadges);
    });

    it('should NOT enter rename mode for Problem Statement (PS is not renamable)', () => {
        comp.repositoryFiles = { [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT };
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();

        // Try to trigger renaming on PS
        const psItem = { value: PROBLEM_STATEMENT_IDENTIFIER, text: PROBLEM_STATEMENT_IDENTIFIER } as TreeViewItem<string>;
        comp.setRenamingFile(psItem);
        fixture.changeDetectorRef.detectChanges();

        // PS must never be put into renaming state
        expect(comp.renamingFile).toBeUndefined();

        // And there must be no rename input rendered for PS
        const anyRenameInput =
            debugElement.query(By.css('jhi-code-editor-file-browser-file input')) ||
            debugElement.query(By.css('jhi-code-editor-file-browser-folder input')) ||
            debugElement.query(By.css('jhi-code-editor-file-browser-problem-statement input'));
        expect(anyRenameInput).toBeNull();
    });

    it('places Problem Statement at the top of the tree', () => {
        fixture.componentRef.setInput('isProblemStatementVisible', true);
        comp.ngOnInit();

        comp.repositoryFiles = {
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
            'b.txt': FileType.FILE,
            'a.txt': FileType.FILE,
        };

        comp.setupTreeview();

        const values = comp.filesTreeViewItem.map((i) => i.value);
        expect(values[0]).toBe(PROBLEM_STATEMENT_IDENTIFIER);
        expect(values.slice(1)).toEqual(['a.txt', 'b.txt']);
    });

    it('adds the Problem Statement entry when isProblemStatementVisible is true', () => {
        // ensure fresh state
        internals(comp).repositoryFiles = undefined;
        fixture.componentRef.setInput('isProblemStatementVisible', true);

        comp.ngOnInit();
        comp.setupTreeview();

        expect(comp.repositoryFiles[PROBLEM_STATEMENT_IDENTIFIER]).toBe(FileType.PROBLEM_STATEMENT);
        expect(comp.filesTreeViewItem.map((i) => i.value)).toContain(PROBLEM_STATEMENT_IDENTIFIER);
    });

    it('removes the Problem Statement entry when isProblemStatementVisible toggles to false', () => {
        // start with PS present
        fixture.componentRef.setInput('isProblemStatementVisible', true);
        comp.ngOnInit();
        internals(comp).handleProblemStatementVisibility?.();

        // toggle isProblemStatementVisible to false (no PS)
        fixture.componentRef.setInput('isProblemStatementVisible', false);
        internals(comp).handleProblemStatementVisibility?.();

        expect(comp.repositoryFiles?.[PROBLEM_STATEMENT_IDENTIFIER]).toBeUndefined();
    });

    it('removes Problem Statement when showEditorInstructions toggles to false', () => {
        fixture.componentRef.setInput('isProblemStatementVisible', true);
        comp.ngOnInit();
        expect(comp.repositoryFiles[PROBLEM_STATEMENT_IDENTIFIER]).toBe(FileType.PROBLEM_STATEMENT);
        // flip instructions visibility (signal input)
        fixture.componentRef.setInput('showEditorInstructions', false);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.repositoryFiles[PROBLEM_STATEMENT_IDENTIFIER]).toBeUndefined();
        expect(comp.filesTreeViewItem.find((i) => i.value === PROBLEM_STATEMENT_IDENTIFIER)).toBeUndefined();
    });

    it('re-adds Problem Statement when showEditorInstructions toggles back to true', () => {
        fixture.componentRef.setInput('isProblemStatementVisible', true);
        fixture.componentRef.setInput('participation', { id: 1 });
        comp.ngOnInit();
        fixture.componentRef.setInput('showEditorInstructions', false);
        fixture.changeDetectorRef.detectChanges();
        fixture.componentRef.setInput('showEditorInstructions', true);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.repositoryFiles[PROBLEM_STATEMENT_IDENTIFIER]).toBe(FileType.PROBLEM_STATEMENT);
        expect(comp.filesTreeViewItem.map((i) => i.value)).toContain(PROBLEM_STATEMENT_IDENTIFIER);
    });

    it('should create no treeviewItems if getRepositoryContent returns an empty result', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();

        expect(comp.isLoadingFiles).toBe(false);
        // repositoryFiles now contains only PS
        expect(comp.repositoryFiles).toEqual({
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });

        // tree now has exactly 1 item: PS
        expect(comp.filesTreeViewItem).toHaveLength(1);
        expect(comp.filesTreeViewItem[0].value).toBe(PROBLEM_STATEMENT_IDENTIFIER);

        // still no regular folders/files rendered
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(0);
    });

    it('opens a delete modal when deleting a folder', () => {
        comp.repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
        };
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();

        const item = { value: 'folder', text: 'folder' } as unknown as TreeViewItem<string>;
        const modalRef = {
            componentInstance: { setInputs: vi.fn() },
        } as any;

        const openSpy = vi.spyOn(comp.modalService, 'open').mockReturnValue(modalRef);

        comp.openDeleteFileModal(item);

        expect(openSpy).toHaveBeenCalledOnce();
        expect(modalRef.componentInstance.setInputs).toHaveBeenCalledWith({
            parent: comp,
            fileNameToDelete: 'folder',
            fileType: FileType.FOLDER,
        });
    });

    it('should create treeviewItems if getRepositoryContent returns files', () => {
        const repositoryContent: { [fileName: string]: string } = { file: 'FILE', folder: 'FOLDER' };
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toEqual({
            ...repositoryContent,
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(1);
        expect(renderedFiles).toHaveLength(1);
    });

    it('should create treeviewItems with nested folder structure', () => {
        comp.repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder2/file2': FileType.FILE,
            'folder2/folder3': FileType.FOLDER,
            'folder2/folder3/file3': FileType.FILE,
        };
        comp.compressFolders = false;
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();
        // after compression
        expect(comp.filesTreeViewItem).toHaveLength(2);
        expect(comp.filesTreeViewItem[0].children).toHaveLength(1);
        expect(comp.filesTreeViewItem[1].children).toHaveLength(2);
        expect(comp.filesTreeViewItem[1].children[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children[1].children).toHaveLength(1);
        const folder = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!;
        expect(folder).toEqual(expect.any(Object));
        expect(folder.children).toHaveLength(1);
        const file1 = folder.children.find(({ value }) => value === 'folder/file1')!;
        expect(file1).toBeDefined();
        expect(file1.children).toEqual([]);
        const folder2 = comp.filesTreeViewItem.find(({ value }) => value === 'folder2')!;
        expect(folder2).toEqual(expect.any(Object));
        expect(folder2.children).toHaveLength(2);
        const file2 = folder2.children.find(({ value }) => value === 'folder2/file2')!;
        expect(file2).toBeDefined();
        expect(file2.children).toEqual([]);
        const folder3 = folder2.children.find(({ value }) => value === 'folder2/folder3')!;
        expect(folder3).toEqual(expect.any(Object));
        expect(folder3.children).toHaveLength(1);
        const file3 = folder3.children.find(({ value }) => value === 'folder2/folder3/file3')!;
        expect(file3).toBeDefined();
        expect(file3.children).toEqual([]);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(3);
        expect(renderedFiles).toHaveLength(3);
    });

    it('should toggle tree compression', () => {
        comp.repositoryFiles = {
            file1: FileType.FILE,
        };
        const treeNode = {
            folder: '',
            file: 'file1',
            children: [],
            text: 'file1',
            value: 'file1',
        };
        vi.spyOn(comp, 'buildTree').mockReturnValue([treeNode]);
        const transformTreeToTreeViewItemStub = vi.spyOn(comp, 'transformTreeToTreeViewItem').mockReturnValue([new TreeViewItem(treeNode)]);
        comp.compressFolders = false;
        comp.toggleTreeCompress();
        expect(comp.compressFolders).toBe(true);
        expect(transformTreeToTreeViewItemStub).toHaveBeenCalledExactlyOnceWith([treeNode]);
    });

    it('should create compressed treeviewItems with nested folder structure', () => {
        comp.repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder2/file2': FileType.FILE,
            'folder2/folder3': FileType.FOLDER,
            'folder2/folder3/file3': FileType.FILE,
            'folder2/folder3/folder4': FileType.FOLDER,
            'folder2/folder3/folder4/folder5': FileType.FOLDER,
        };
        comp.compressFolders = true;
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();
        // after compression
        expect(comp.filesTreeViewItem).toHaveLength(2);
        expect(comp.filesTreeViewItem[0].children).toHaveLength(1);
        expect(comp.filesTreeViewItem[0].children[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children).toHaveLength(2);
        expect(comp.filesTreeViewItem[1].children[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children[1].children).toHaveLength(2);
        expect(comp.filesTreeViewItem[1].children[1].children[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children[1].children[1].children).toHaveLength(0);
        const folder = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!;
        expect(folder).toEqual(expect.any(Object));
        const file1 = folder.children.find(({ value }) => value === 'folder/file1')!;
        expect(file1).toEqual(expect.any(Object));
        expect(file1.children).toEqual([]);
        const folder2 = comp.filesTreeViewItem.find(({ value }) => value === 'folder2')!;
        expect(folder2).toEqual(expect.any(Object));
        expect(folder2.children).toHaveLength(2);
        const file2 = folder2.children.find(({ value }) => value === 'folder2/file2')!;
        expect(file2).toBeDefined();
        expect(file2.children).toEqual([]);
        const folder3 = folder2.children.find(({ value }) => value === 'folder2/folder3')!;
        expect(folder3).toEqual(expect.any(Object));
        const file3 = folder3.children.find(({ value }) => value === 'folder2/folder3/file3')!;
        expect(file3).toBeDefined();
        expect(file3.children).toEqual([]);
        const folder4 = folder3.children.find(({ value }) => value === 'folder2/folder3/folder4')!;
        expect(folder4).toBeUndefined();
        const folder5 = folder3.children.find(({ value }) => value === 'folder2/folder3/folder4/folder5')!;
        expect(folder5).toEqual(expect.any(Object));
        expect(folder5.children).toEqual([]);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(4);
        expect(renderedFiles).toHaveLength(3);
    });

    it('should filter forbidden files from getRepositoryContent response', () => {
        const allowedFiles = {
            'allowedFile.java': FileType.FILE,
        };
        const forbiddenFiles = {
            'danger.bin': FileType.FILE,
            '.hidden': FileType.FILE,
            '.': FileType.FOLDER,
        };
        const repositoryContent = {
            ...allowedFiles,
            ...forbiddenFiles,
        };
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toEqual({
            ...allowedFiles,
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });

        // tree should contain exactly the allowed file and PS
        expect(comp.filesTreeViewItem).toHaveLength(2);
        const values = comp.filesTreeViewItem.map((i) => i.value);
        expect(values).toContain('allowedFile.java');
        expect(values).toContain(PROBLEM_STATEMENT_IDENTIFIER);

        // rendered components: one file, zero folders
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(1);
    });

    it('should show folders with dots in their names', () => {
        const allowedFolders = {
            'dot.in.folderName': FileType.FOLDER,
            'regular.folder': FileType.FOLDER,
        };
        const hiddenFolders = {
            '.git': FileType.FOLDER,
            '.other_hidden_folder': FileType.FOLDER,
        };
        const allFolders = {
            ...allowedFolders,
            ...hiddenFolders,
        };
        getRepositoryContentStub.mockReturnValue(of(allFolders));

        comp.loadFiles().subscribe((filteredFiles) => {
            const actualFiles = Object.keys(filteredFiles);
            expect(actualFiles).toHaveLength(2);
            expect(actualFiles).toEqual(Object.keys(allowedFolders));
        });
    });

    it('should not load files if commitState could not be retrieved (possibly corrupt repository or server error)', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        const loadFilesSpy = vi.spyOn(comp, 'loadFiles');
        getStatusStub.mockReturnValue(isCleanSubject);
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(true);
        expect(comp.commitState()).toEqual(CommitState.UNDEFINED);
        isCleanSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.commitState()).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(comp.isLoadingFiles).toBe(false);

        // PS is still present
        expect(comp.repositoryFiles).toEqual({
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });

        // tree was built to show PS
        expect(comp.filesTreeViewItem).toHaveLength(1);
        expect(comp.filesTreeViewItem[0].value).toBe(PROBLEM_STATEMENT_IDENTIFIER);

        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(loadFilesSpy).not.toHaveBeenCalled();

        // still no regular folders/files rendered
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(0);
    });

    it('should set isLoading false and emit an error if loadFiles fails', () => {
        const isCleanSubject = new Subject<{ repositoryStatus: string }>();
        const getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        getStatusStub.mockReturnValue(isCleanSubject);
        getRepositoryContentStub.mockReturnValue(getRepositoryContentSubject);
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(true);
        expect(comp.commitState()).toEqual(CommitState.UNDEFINED);
        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });
        getRepositoryContentSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toEqual({
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });
        expect(comp.filesTreeViewItem).toHaveLength(1);
        expect(comp.filesTreeViewItem[0].value).toBe(PROBLEM_STATEMENT_IDENTIFIER);

        expect(onErrorSpy).toHaveBeenCalledOnce();

        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(0);
    });

    it('should select the correct file based on the user selection', () => {
        const fileToSelect = 'folder/file1';
        const otherFile = 'folder2/file2';
        comp.repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder/file2': FileType.FILE,
        };
        comp.filesTreeViewItem = [
            new TreeViewItem({
                checked: false,
                text: fileToSelect,
                value: fileToSelect,
                children: [],
            } as any),
            new TreeViewItem({
                checked: false,
                text: otherFile,
                value: otherFile,
                children: [],
            }),
        ];
        fixture.componentRef.setInput('selectedFile', undefined);
        fixture.detectChanges();
        const selectedFileChangeSpy = vi.spyOn(comp.selectedFileChange, 'emit');
        const nodeFirstFile = comp.filesTreeViewItem[0];
        comp.handleNodeSelected(nodeFirstFile);
        expect(nodeFirstFile.checked).toBe(true);
        expect(selectedFileChangeSpy).toHaveBeenCalledWith(fileToSelect);
        // Deselect the current file.
        const nodeSecondFile = comp.filesTreeViewItem[1];
        comp.handleNodeSelected(nodeSecondFile);
        expect(nodeFirstFile.checked).toBe(false);
        expect(nodeSecondFile.checked).toBe(true);
        expect(selectedFileChangeSpy).toHaveBeenCalledWith(otherFile);
    });

    it('should set node to checked if its file gets selected and update ui', () => {
        const selectedFile = 'folder/file1';
        const repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder/file2': FileType.FILE,
        };
        comp.repositoryFiles = repositoryFiles;
        fixture.detectChanges();
        fixture.componentRef.setInput('selectedFile', selectedFile);
        fixture.detectChanges();
        expect(comp.selectedFile()).toEqual(selectedFile);
        const selectedTreeItem = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!.children.find(({ value }) => value === selectedFile)!;
        expect(selectedTreeItem).toBeDefined();
        expect(selectedTreeItem.checked).toBe(true);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(2);
        expect(renderedFiles).toHaveLength(2);

        const selectedFileHtml = renderedFiles[0];
        const isSelected = !!selectedFileHtml.query(By.css('.node-selected'));
        expect(isSelected).toBe(true);
        const notSelectedFilesHtml = [renderedFiles[1], ...renderedFolders];
        const areUnSelected = !notSelectedFilesHtml.some((el) => !!el.query(By.css('.node-selected')));
        expect(areUnSelected).toBe(true);
    });

    it('should add file to node tree if created', () => {
        const fileName = 'file2';
        const filePath = 'folder2/file2';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = vi.spyOn(comp.onFileChange, 'emit');
        const setupTreeviewSpy = vi.spyOn(comp, 'setupTreeview');
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FILE];
        fixture.changeDetectorRef.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).not.toBeNull();
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).not.toBeNull();

        // The create-node component focuses its input synchronously in ngAfterViewInit (no timer).
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).toEqual(focusedElement);

        comp.onCreateFile(fileName);
        fixture.changeDetectorRef.detectChanges();

        expect(createFileStub).toHaveBeenCalledOnce();
        expect(createFileStub).toHaveBeenCalledWith(filePath);
        expect(comp.creatingFile).toBeUndefined();
        expect(setupTreeviewSpy).toHaveBeenCalledOnce();
        expect(setupTreeviewSpy).toHaveBeenCalledWith();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({ ...repositoryFiles, [filePath]: FileType.FILE });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).toBeNull();
    });

    it('should add folder to node tree if created', () => {
        const fileName = 'folder3';
        const filePath = 'folder2/folder3';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = vi.spyOn(comp.onFileChange, 'emit');
        const setupTreeviewSpy = vi.spyOn(comp, 'setupTreeview');
        const createFolderStub = vi.spyOn(codeEditorRepositoryFileService, 'createFolder').mockReturnValue(of(undefined));
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FOLDER];
        fixture.changeDetectorRef.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).not.toBeNull();
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).not.toBeNull();

        // The create-node component focuses its input synchronously in ngAfterViewInit (no timer).
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).toEqual(focusedElement);

        comp.onCreateFile(fileName);
        fixture.changeDetectorRef.detectChanges();

        expect(createFolderStub).toHaveBeenCalledOnce();
        expect(createFolderStub).toHaveBeenCalledWith(filePath);
        expect(comp.creatingFile).toBeUndefined();
        expect(setupTreeviewSpy).toHaveBeenCalledOnce();
        expect(setupTreeviewSpy).toHaveBeenCalledWith();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({ ...repositoryFiles, [filePath]: FileType.FOLDER });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).toBeNull();
    });

    it('should not be able to create binary file', () => {
        const fileName = 'danger.bin';
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        comp.creatingFile = ['', FileType.FILE];
        comp.onCreateFile(fileName);
        fixture.changeDetectorRef.detectChanges();
        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(createFileStub).not.toHaveBeenCalled();
    });

    it('should be able to create a folder with a name that contains dots', () => {
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');

        const folderName = 'dot.in.folderName';
        comp.creatingFile = ['', FileType.FOLDER];
        comp.repositoryFiles = {};
        comp.onCreateFile(folderName);
        fixture.changeDetectorRef.detectChanges();

        expect(onErrorSpy).not.toHaveBeenCalled();
    });

    it.each([FileType.FILE, FileType.FOLDER])('should not be able to create a hidden %s', (fileType) => {
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');

        const name = '.hidden_file_or_folder';
        comp.creatingFile = ['', fileType];
        comp.repositoryFiles = {};
        comp.onCreateFile(name);
        fixture.changeDetectorRef.detectChanges();

        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(onErrorSpy).toHaveBeenCalledWith('unsupportedFile');
    });

    it('should not be able to create node that already exists', () => {
        const fileName = 'file1';
        const repositoryFiles = { 'folder2/file1': FileType.FILE, folder2: FileType.FOLDER };
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        comp.creatingFile = ['folder2', FileType.FILE];
        comp.repositoryFiles = repositoryFiles;
        comp.onCreateFile(fileName);
        fixture.changeDetectorRef.detectChanges();
        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(createFileStub).not.toHaveBeenCalled();
    });

    it('should manage the root file/folder it is currently creating', () => {
        comp.setCreatingFileInRoot(FileType.FILE);
        expect(comp.creatingFile).toEqual(['', FileType.FILE]);
        comp.setCreatingFileInRoot(FileType.FOLDER);
        expect(comp.creatingFile).toEqual(['', FileType.FOLDER]);
        comp.clearCreatingFile();
        expect(comp.creatingFile).toBeUndefined();
    });

    it('should manage the file/folder it is currently creating within another folder', () => {
        const folder = 'folder';
        const item = { value: folder } as TreeViewItem<string>;
        comp.setCreatingFile({ item, fileType: FileType.FILE });
        expect(comp.creatingFile).toEqual([folder, FileType.FILE]);
        comp.setCreatingFile({ item, fileType: FileType.FOLDER });
        expect(comp.creatingFile).toEqual([folder, FileType.FOLDER]);
        comp.clearCreatingFile();
        expect(comp.creatingFile).toBeUndefined();
    });

    it('should update repository file entry on rename', () => {
        vi.useFakeTimers();
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const treeItems = [
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const onFileChangeSpy = vi.spyOn(comp.onFileChange, 'emit');
        comp.repositoryFiles = repositoryFiles;
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        comp.filesTreeViewItem = treeItems;
        fixture.changeDetectorRef.detectChanges();

        let filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(1);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(1);
        let renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // The node component focuses the rename input via a setTimeout(0) scheduled from an effect.
        vi.runAllTimers();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.changeDetectorRef.detectChanges();

        expect(renameFileStub).toHaveBeenCalledOnce();
        expect(renameFileStub).toHaveBeenCalledWith(fileName, afterRename);
        expect(comp.renamingFile).toBeUndefined();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({
            folder2: FileType.FOLDER,
            [afterRename]: FileType.FILE,
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });
        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(1);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(1);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeNull();

        vi.runAllTimers();
        vi.useRealTimers();
    });

    it('should rename all paths concerned if a folder is renamed', () => {
        vi.useFakeTimers();
        const folderName = 'folder';
        const afterRename = 'newFolderName';
        const treeItems = [
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'folder/file1',
            } as any),
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file2',
                value: 'folder/file2',
            } as any),
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder',
                value: 'folder',
            } as any),
            new TreeViewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const repositoryFiles = { 'folder/file1': FileType.FILE, 'folder/file2': FileType.FILE, folder: FileType.FOLDER, folder2: FileType.FOLDER };
        const onFileChangeSpy = vi.spyOn(comp.onFileChange, 'emit');
        comp.repositoryFiles = repositoryFiles;
        comp.renamingFile = [folderName, folderName, FileType.FILE];
        comp.filesTreeViewItem = treeItems;
        fixture.changeDetectorRef.detectChanges();

        let filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(2);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(2);
        let renamingInput = foldersInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // The node component focuses the rename input via a setTimeout(0) scheduled from an effect.
        vi.runAllTimers();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));

        expect(renameFileStub).toHaveBeenCalledOnce();
        expect(renameFileStub).toHaveBeenCalledWith(folderName, afterRename);
        expect(comp.renamingFile).toBeUndefined();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({
            [[afterRename, 'file1'].join('/')]: FileType.FILE,
            [[afterRename, 'file2'].join('/')]: FileType.FILE,
            [afterRename]: FileType.FOLDER,
            folder2: FileType.FOLDER,
            [PROBLEM_STATEMENT_IDENTIFIER]: FileType.PROBLEM_STATEMENT,
        });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(2);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(2);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeNull();
        vi.useRealTimers();
    });

    it('should not rename a file if its new fileName already exists in the repository', () => {
        vi.useFakeTimers();
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.changeDetectorRef.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // The node component focuses the rename input via a setTimeout(0) scheduled from an effect.
        vi.runAllTimers();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.changeDetectorRef.detectChanges();

        expect(renameFileStub).not.toHaveBeenCalled();
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);
        vi.useRealTimers();
    });

    it('should not rename a file if its new fileName indicates a binary file', () => {
        vi.useFakeTimers();
        const fileName = 'file1';
        const afterRename = 'newFileName.bin';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.changeDetectorRef.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // The node component focuses the rename input via a setTimeout(0) scheduled from an effect.
        vi.runAllTimers();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.changeDetectorRef.detectChanges();

        expect(renameFileStub).not.toHaveBeenCalled();
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);
        vi.useRealTimers();
    });

    it('should be able to rename a folder with a new  name that contains dots', () => {
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');

        const newFolderName = 'dot.in.folderName';

        comp.renamingFile = ['', 'oldFolderName', FileType.FOLDER];
        comp.repositoryFiles = { oldFolderName: FileType.FOLDER };
        comp.onRenameFile(newFolderName);
        fixture.changeDetectorRef.detectChanges();

        expect(onErrorSpy).not.toHaveBeenCalled();
    });

    it.each([FileType.FILE, FileType.FOLDER])('should not be able to rename a %s so that is hidden', (fileType) => {
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');

        const newName = '.hidden_file_or_folder';

        comp.renamingFile = ['', 'oldName', fileType];
        comp.repositoryFiles = { oldName: fileType };
        comp.onRenameFile(newName);
        fixture.changeDetectorRef.detectChanges();

        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(onErrorSpy).toHaveBeenCalledWith('unsupportedFile');
    });

    it('should leave rename state if renaming a file to the same file name', () => {
        vi.useFakeTimers();
        const fileName = 'file1';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.changeDetectorRef.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.changeDetectorRef.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // The node component focuses the rename input via a setTimeout(0) scheduled from an effect.
        vi.runAllTimers();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.changeDetectorRef.detectChanges();

        expect(renameFileStub).not.toHaveBeenCalled();
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).toBeNull();
        vi.useRealTimers();
    });

    it('should manage the file it is currently renaming', () => {
        comp.repositoryFiles = {
            'folder/file1': FileType.FILE,
            folder: FileType.FOLDER,
        };
        const item = { value: 'folder/file1', text: 'file1' } as TreeViewItem<string>;
        comp.setRenamingFile(item);
        expect(comp.renamingFile).toEqual(['folder/file1', 'file1', FileType.FILE]);
        comp.clearRenamingFile();
        expect(comp.renamingFile).toBeUndefined();
    });

    it('should disable action buttons if there is a git conflict', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CONFLICT }));
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();

        expect(comp.commitState()).toEqual(CommitState.CONFLICT);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).toBe(true);
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).toBe(true);
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).toBe(false);

        // Resolve conflict.
        conflictService.notifyConflictState(GitConflictState.OK);
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));

        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        fixture.detectChanges();

        expect(comp.commitState()).toEqual(CommitState.CLEAN);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).toBe(false);
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).toBe(false);
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).toBe(false);

        expect(getRepositoryContentStub).toHaveBeenCalledOnce();
        expect(comp.selectedFile()).toBeUndefined();
    });

    it('should load information about changed files', () => {
        const changeInformation = {
            'Class.java': true,
            'Document.md': false,
            'README.md': true,
            'binary1.exe': true,
            'binary2.zip': false,
        };
        const filteredChangeInformation = {
            'Class.java': true,
            'Document.md': false,
            'README.md': true,
        };
        const getFilesWithChangeInfoStub = vi.fn().mockReturnValue(of(changeInformation));
        codeEditorRepositoryFileService.getFilesWithInformationAboutChange = getFilesWithChangeInfoStub;

        const loadFiles = comp.loadFilesWithInformationAboutChange().subscribe((result) => {
            expect(result).toEqual(filteredChangeInformation);
        });

        expect(getFilesWithChangeInfoStub).toHaveBeenCalledOnce();

        loadFiles.unsubscribe();
    });

    it('should open a modal when trying to delete a file', () => {
        comp.repositoryFiles = {
            'folder/file1': FileType.FILE,
            folder: FileType.FOLDER,
        };
        const item = { value: 'folder/file1', text: 'file1' } as TreeViewItem<string>;
        const modalRef = { componentInstance: { setInputs: vi.fn() } } as unknown as NgbModalRef;
        const openModalStub = vi.spyOn(comp.modalService, 'open').mockReturnValue(modalRef);
        comp.openDeleteFileModal(item);
        expect(openModalStub).toHaveBeenCalledOnce();
        expect(modalRef.componentInstance.setInputs).toHaveBeenCalledWith({
            parent: comp,
            fileNameToDelete: 'folder/file1',
            fileType: FileType.FILE,
        });
    });

    describe('getFolderBadges', () => {
        // Mock fileBadges data
        const mockFileBadges = {
            'folderA/file': [new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 1)],
            'folderB/file1': [new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 1)],
            'folderB/file2': [new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 2), new FileBadge(FileBadgeType.REVIEW_COMMENT, 1)],
        };

        beforeEach(() => {
            fixture.componentRef.setInput('fileBadges', mockFileBadges);
            fixture.detectChanges();
        });

        it('should return an empty array if folder is not collapsed', () => {
            const result = comp.getFolderBadges({ value: 'folderA', collapsed: false } as TreeViewItem<string>);
            expect(result).toEqual([]);
        });

        it('should work for single file badge for a collapsed folder', () => {
            const result = comp.getFolderBadges({ value: 'folderA', collapsed: true } as TreeViewItem<string>);
            expect(result).toEqual([new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 1)]);
        });

        it('should aggregate file badges for a collapsed folder', () => {
            const result = comp.getFolderBadges({ value: 'folderB', collapsed: true } as TreeViewItem<string>);
            expect(result).toEqual([new FileBadge(FileBadgeType.FEEDBACK_SUGGESTION, 3), new FileBadge(FileBadgeType.REVIEW_COMMENT, 1)]); // 1 + 2 suggestions, 1 review thread
        });
    });

    describe('file sync service integration', () => {
        let mockSyncService: Pick<CodeEditorFileSyncService, 'emitFileCreated' | 'emitFileDeleted' | 'emitFileRenamed'>;

        beforeEach(() => {
            mockSyncService = {
                emitFileCreated: vi.fn(),
                emitFileDeleted: vi.fn(),
                emitFileRenamed: vi.fn(),
            };
            fixture.componentRef.setInput('fileSyncService', mockSyncService);
            fixture.detectChanges();
        });

        it('should call emitFileDeleted on file deletion', () => {
            comp.repositoryFiles = { 'src/File.java': FileType.FILE };
            comp.setupTreeview();
            fixture.changeDetectorRef.detectChanges();

            comp.onFileDeleted(new DeleteFileChange(FileType.FILE, 'src/File.java'));

            expect(mockSyncService.emitFileDeleted).toHaveBeenCalledWith('src/File.java', 'FILE');
        });

        it('should call emitFileDeleted with FOLDER for folder deletion', () => {
            comp.repositoryFiles = { 'src/pkg': FileType.FOLDER };
            comp.setupTreeview();
            fixture.changeDetectorRef.detectChanges();

            comp.onFileDeleted(new DeleteFileChange(FileType.FOLDER, 'src/pkg'));

            expect(mockSyncService.emitFileDeleted).toHaveBeenCalledWith('src/pkg', 'FOLDER');
        });

        it('should call emitFileRenamed on file rename', () => {
            comp.repositoryFiles = { 'oldFile.java': FileType.FILE };
            comp.setupTreeview();
            comp.renamingFile = ['oldFile.java', 'oldFile.java', FileType.FILE];
            fixture.changeDetectorRef.detectChanges();

            comp.onRenameFile('newFile.java');

            expect(mockSyncService.emitFileRenamed).toHaveBeenCalledWith('oldFile.java', 'newFile.java', 'FILE');
        });

        it('should call emitFileCreated on file creation', () => {
            comp.repositoryFiles = {};
            comp.creatingFile = ['', FileType.FILE];
            fixture.changeDetectorRef.detectChanges();

            comp.onCreateFile('NewFile.java');

            expect(mockSyncService.emitFileCreated).toHaveBeenCalledWith('NewFile.java', 'FILE');
        });

        it('should call emitFileCreated with FOLDER on folder creation', () => {
            const createFolderStub = vi.spyOn(codeEditorRepositoryFileService, 'createFolder').mockReturnValue(of(undefined));
            comp.repositoryFiles = {};
            comp.creatingFile = ['', FileType.FOLDER];
            fixture.changeDetectorRef.detectChanges();

            comp.onCreateFile('newpkg');

            expect(createFolderStub).toHaveBeenCalledWith('newpkg');
            expect(mockSyncService.emitFileCreated).toHaveBeenCalledWith('newpkg', 'FOLDER');
        });
    });

    describe('handleFileChange isRemote propagation', () => {
        it('emits isRemote=true as the third tuple element when called with isRemote=true', () => {
            comp.repositoryFiles = {};
            const emitted: [string[], FileChange, boolean?][] = [];
            comp.onFileChange.subscribe((event) => emitted.push(event));

            comp.handleFileChange(new CreateFileChange(FileType.FILE, 'src/Remote.java'), true);

            expect(emitted).toHaveLength(1);
            expect(emitted[0][2]).toBe(true);
        });

        it('emits isRemote=false as the third tuple element when called without the parameter', () => {
            comp.repositoryFiles = {};
            const emitted: [string[], FileChange, boolean?][] = [];
            comp.onFileChange.subscribe((event) => emitted.push(event));

            comp.handleFileChange(new CreateFileChange(FileType.FILE, 'src/Local.java'));

            expect(emitted).toHaveLength(1);
            expect(emitted[0][2]).toBe(false);
        });
    });
});
