import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { Subject, of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CommitState, FileType, GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorConflictStateService } from '../../helpers/mocks/service/mock-code-editor-conflict-state.service';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TreeviewModule } from 'app/exercises/programming/shared/code-editor/treeview/treeview.module';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';

describe('CodeEditorFileBrowserComponent', () => {
    let comp: CodeEditorFileBrowserComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let conflictService: CodeEditorConflictStateService;
    let getRepositoryContentStub: jest.SpyInstance;
    let getStatusStub: jest.SpyInstance;
    let createFileStub: jest.SpyInstance;
    let renameFileStub: jest.SpyInstance;

    const createFileRoot = '#create_file_root';
    const createFolderRoot = '#create_folder_root';
    const compressTree = '#compress_tree';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TreeviewModule.forRoot()],
            declarations: [
                CodeEditorFileBrowserComponent,
                CodeEditorFileBrowserFileComponent,
                CodeEditorFileBrowserFolderComponent,
                CodeEditorFileBrowserCreateNodeComponent,
                MockComponent(CodeEditorStatusComponent),
                TranslatePipeMock,
            ],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorFileBrowserComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = TestBed.inject(CodeEditorRepositoryFileService);
                codeEditorRepositoryService = TestBed.inject(CodeEditorRepositoryService);
                conflictService = TestBed.inject(CodeEditorConflictStateService);
                getStatusStub = jest.spyOn(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = jest.spyOn(codeEditorRepositoryFileService, 'getRepositoryContent');
                createFileStub = jest.spyOn(codeEditorRepositoryFileService, 'createFile').mockReturnValue(of(undefined));
                renameFileStub = jest.spyOn(codeEditorRepositoryFileService, 'renameFile').mockReturnValue(of(undefined));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create no treeviewItems if getRepositoryContent returns an empty result', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        const expectedFileTreeItems: TreeviewItem<string>[] = [];
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.isLoadingFiles).toBeFalse();
        expect(comp.repositoryFiles).toEqual(repositoryContent);
        expect(comp.filesTreeViewItem).toEqual(expectedFileTreeItems);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(0);
    });

    it('should create treeviewItems if getRepositoryContent returns files', () => {
        const repositoryContent: { [fileName: string]: string } = { file: 'FILE', folder: 'FOLDER' };
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBeFalse();
        expect(comp.repositoryFiles).toEqual(repositoryContent);
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
        fixture.detectChanges();
        // after compression
        expect(comp.filesTreeViewItem).toHaveLength(2);
        expect(comp.filesTreeViewItem[0].children).toHaveLength(1);
        expect(comp.filesTreeViewItem[1].children).toHaveLength(2);
        expect(comp.filesTreeViewItem[1].children[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children[1].children).toHaveLength(1);
        const folder = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!;
        expect(folder).toBeObject();
        expect(folder.children).toHaveLength(1);
        const file1 = folder.children.find(({ value }) => value === 'folder/file1')!;
        expect(file1).toBeDefined();
        expect(file1.children).toEqual([]);
        const folder2 = comp.filesTreeViewItem.find(({ value }) => value === 'folder2')!;
        expect(folder2).toBeObject();
        expect(folder2.children).toHaveLength(2);
        const file2 = folder2.children.find(({ value }) => value === 'folder2/file2')!;
        expect(file2).toBeDefined();
        expect(file2.children).toEqual([]);
        const folder3 = folder2.children.find(({ value }) => value === 'folder2/folder3')!;
        expect(folder3).toBeObject();
        expect(folder3.children).toHaveLength(1);
        const file3 = folder3.children.find(({ value }) => value === 'folder2/folder3/file3')!;
        expect(file3).toBeDefined();
        expect(file3.children).toEqual([]);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(3);
        expect(renderedFiles).toHaveLength(3);
    });

    it('should create compressed treeviewItems with nested folder structure', () => {
        comp.repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder2/file2': FileType.FILE,
            'folder2/folder3': FileType.FOLDER,
            'folder2/folder3/file3': FileType.FILE,
        };
        comp.compressFolders = true;
        comp.setupTreeview();
        fixture.detectChanges();
        // after compression
        expect(comp.filesTreeViewItem).toHaveLength(2);
        expect(comp.filesTreeViewItem[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children).toHaveLength(2);
        expect(comp.filesTreeViewItem[1].children[0].children).toHaveLength(0);
        expect(comp.filesTreeViewItem[1].children[1].children).toHaveLength(0);
        const folder = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!;
        expect(folder).toBeUndefined();
        const file1 = comp.filesTreeViewItem.find(({ value }) => value === 'folder/file1')!;
        expect(file1).toBeObject();
        expect(file1.children).toEqual([]);
        const folder2 = comp.filesTreeViewItem.find(({ value }) => value === 'folder2')!;
        expect(folder2).toBeObject();
        expect(folder2.children).toHaveLength(2);
        const file2 = folder2.children.find(({ value }) => value === 'folder2/file2')!;
        expect(file2).toBeDefined();
        expect(file2.children).toEqual([]);
        const folder3 = folder2.children.find(({ value }) => value === 'folder2/folder3')!;
        expect(folder3).toBeUndefined();
        const file3 = folder2.children.find(({ value }) => value === 'folder2/folder3/file3')!;
        expect(file3).toBeDefined();
        expect(file3.children).toEqual([]);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(1);
        expect(renderedFiles).toHaveLength(3);
    });

    it('should filter forbidden files from getRepositoryContent response', () => {
        const allowedFiles = {
            'allowedFile.java': FileType.FILE,
        };
        const forbiddenFiles = {
            'danger.bin': FileType.FILE,
            'README.md': FileType.FILE,
            '.hidden': FileType.FILE,
            '.': FileType.FOLDER,
        };
        const repositoryContent = {
            ...allowedFiles,
            ...forbiddenFiles,
        };
        const expectedFileTreeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
        ].map((x) => x.toString());
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBeFalse();
        expect(comp.repositoryFiles).toEqual(allowedFiles);
        expect(comp.filesTreeViewItem.map((x) => x.toString())).toEqual(expectedFileTreeItems);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(1);
    });

    it('should show folders with dots in their names', fakeAsync(() => {
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

        flush();
    }));

    it('should not load files if commitState could not be retrieved (possibly corrupt repository or server error)', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        const loadFilesSpy = jest.spyOn(comp, 'loadFiles');
        getStatusStub.mockReturnValue(isCleanSubject);
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBeTrue();
        expect(comp.commitState).toEqual(CommitState.UNDEFINED);
        isCleanSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.commitState).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(comp.isLoadingFiles).toBeFalse();
        expect(comp.repositoryFiles).toBeUndefined();
        expect(comp.filesTreeViewItem).toBeUndefined();
        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(loadFilesSpy).not.toHaveBeenCalled();
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(0);
    });

    it('should set isLoading false and emit an error if loadFiles fails', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        getStatusStub.mockReturnValue(isCleanSubject);
        getRepositoryContentStub.mockReturnValue(getRepositoryContentSubject);
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBeTrue();
        expect(comp.commitState).toEqual(CommitState.UNDEFINED);
        isCleanSubject.next({ isClean: true });
        getRepositoryContentSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBeFalse();
        expect(comp.repositoryFiles).toBeUndefined();
        expect(comp.filesTreeViewItem).toBeUndefined();
        expect(onErrorSpy).toHaveBeenCalledOnce();
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(0);
        expect(renderedFiles).toHaveLength(0);
    });

    it('should set node to checked if its file gets selected and update ui', () => {
        const selectedFile = 'folder/file1';
        const repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
            folder2: FileType.FOLDER,
            'folder/file2': FileType.FILE,
        };
        comp.filesTreeViewItem = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: selectedFile,
                value: 'file1',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2/file2',
                value: 'file2',
            } as any),
        ];
        comp.repositoryFiles = repositoryFiles;
        comp.selectedFile = selectedFile;
        triggerChanges(comp, { property: 'selectedFile', currentValue: 'folder/file2', firstChange: false });
        fixture.detectChanges();
        expect(comp.selectedFile).toEqual(selectedFile);
        const selectedTreeItem = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!.children.find(({ value }) => value === selectedFile)!;
        expect(selectedTreeItem).toBeDefined();
        expect(selectedTreeItem.checked).toBeTrue();
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders).toHaveLength(2);
        expect(renderedFiles).toHaveLength(2);

        const selectedFileHtml = renderedFiles[0];
        const isSelected = !!selectedFileHtml.query(By.css('.node-selected'));
        expect(isSelected).toBeTrue();
        const notSelectedFilesHtml = [renderedFiles[1], ...renderedFolders];
        const areUnSelected = !notSelectedFilesHtml.some((el) => !!el.query(By.css('.node-selected')));
        expect(areUnSelected).toBeTrue();
    });

    it('should add file to node tree if created', fakeAsync(() => {
        const fileName = 'file2';
        const filePath = 'folder2/file2';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = jest.spyOn(comp.onFileChange, 'emit');
        const setupTreeviewSpy = jest.spyOn(comp, 'setupTreeview');
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FILE];
        fixture.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).not.toBeNull();
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).not.toBeNull();

        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).toEqual(focusedElement);

        comp.onCreateFile(fileName);
        fixture.detectChanges();
        tick();

        expect(createFileStub).toHaveBeenCalledOnce();
        expect(createFileStub).toHaveBeenCalledWith(filePath);
        expect(comp.creatingFile).toBeUndefined();
        expect(setupTreeviewSpy).toHaveBeenCalledOnce();
        expect(setupTreeviewSpy).toHaveBeenCalledWith();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({ ...repositoryFiles, [filePath]: FileType.FILE });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).toBeNull();
    }));

    it('should add folder to node tree if created', fakeAsync(() => {
        const fileName = 'folder3';
        const filePath = 'folder2/folder3';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = jest.spyOn(comp.onFileChange, 'emit');
        const setupTreeviewSpy = jest.spyOn(comp, 'setupTreeview');
        const createFolderStub = jest.spyOn(codeEditorRepositoryFileService, 'createFolder').mockReturnValue(of(undefined));
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FOLDER];
        fixture.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).not.toBeNull();
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).not.toBeNull();

        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).toEqual(focusedElement);

        comp.onCreateFile(fileName);
        fixture.detectChanges();
        tick();

        expect(createFolderStub).toHaveBeenCalledOnce();
        expect(createFolderStub).toHaveBeenCalledWith(filePath);
        expect(comp.creatingFile).toBeUndefined();
        expect(setupTreeviewSpy).toHaveBeenCalledOnce();
        expect(setupTreeviewSpy).toHaveBeenCalledWith();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({ ...repositoryFiles, [filePath]: FileType.FOLDER });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).toBeNull();
    }));

    it('should not be able to create binary file', () => {
        const fileName = 'danger.bin';
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        comp.creatingFile = ['', FileType.FILE];
        comp.onCreateFile(fileName);
        fixture.detectChanges();
        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(createFileStub).not.toHaveBeenCalled();
    });

    it('should be able to create a folder with a name that contains dots', () => {
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');

        const folderName = 'dot.in.folderName';
        comp.creatingFile = ['', FileType.FOLDER];
        comp.repositoryFiles = {};
        comp.onCreateFile(folderName);
        fixture.detectChanges();

        expect(onErrorSpy).not.toHaveBeenCalled();
    });

    it.each([FileType.FILE, FileType.FOLDER])('should not be able to create a hidden %s', (fileType) => {
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');

        const name = '.hidden_file_or_folder';
        comp.creatingFile = ['', fileType];
        comp.repositoryFiles = {};
        comp.onCreateFile(name);
        fixture.detectChanges();

        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(onErrorSpy).toHaveBeenCalledWith('unsupportedFile');
    });

    it('should not be able to create node that already exists', () => {
        const fileName = 'file1';
        const repositoryFiles = { 'folder2/file1': FileType.FILE, folder2: FileType.FOLDER };
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        comp.creatingFile = ['folder2', FileType.FILE];
        comp.repositoryFiles = repositoryFiles;
        comp.onCreateFile(fileName);
        fixture.detectChanges();
        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(createFileStub).not.toHaveBeenCalled();
    });

    it('should update repository file entry on rename', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const treeItems = [
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const onFileChangeSpy = jest.spyOn(comp.onFileChange, 'emit');
        comp.repositoryFiles = repositoryFiles;
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        comp.filesTreeViewItem = treeItems;
        fixture.detectChanges();

        let filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(1);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(1);
        let renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).toHaveBeenCalledOnce();
        expect(renameFileStub).toHaveBeenCalledWith(fileName, afterRename);
        expect(comp.renamingFile).toBeUndefined();
        expect(onFileChangeSpy).toHaveBeenCalledOnce();
        expect(comp.repositoryFiles).toEqual({ folder2: FileType.FOLDER, [afterRename]: FileType.FILE });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(1);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(1);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeNull();

        // Wait for focus of input element
        tick();
    }));

    it('should rename all paths concerned if a folder is renamed', fakeAsync(() => {
        const folderName = 'folder';
        const afterRename = 'newFolderName';
        const treeItems = [
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'folder/file1',
            } as any),
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file2',
                value: 'folder/file2',
            } as any),
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder',
                value: 'folder',
            } as any),
            new TreeviewItem<string>({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const repositoryFiles = { 'folder/file1': FileType.FILE, 'folder/file2': FileType.FILE, folder: FileType.FOLDER, folder2: FileType.FOLDER };
        const onFileChangeSpy = jest.spyOn(comp.onFileChange, 'emit');
        comp.repositoryFiles = repositoryFiles;
        comp.renamingFile = [folderName, folderName, FileType.FILE];
        comp.filesTreeViewItem = treeItems;
        fixture.detectChanges();

        let filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(2);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(2);
        let renamingInput = foldersInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // Wait for focus of input element
        tick();
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
        });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml).toHaveLength(2);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml).toHaveLength(2);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeNull();
    }));

    it('should not rename a file if its new fileName already exists in the repository', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).not.toHaveBeenCalled();
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);
    }));

    it('should not rename a file if its new fileName indicates a binary file', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName.bin';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).not.toHaveBeenCalled();
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);
    }));

    it('should be able to rename a folder with a new  name that contains dots', () => {
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');

        const newFolderName = 'dot.in.folderName';

        comp.renamingFile = ['', 'oldFolderName', FileType.FOLDER];
        comp.repositoryFiles = { oldFolderName: FileType.FOLDER };
        comp.onRenameFile(newFolderName);
        fixture.detectChanges();

        expect(onErrorSpy).not.toHaveBeenCalled();
    });

    it.each([FileType.FILE, FileType.FOLDER])('should not be able to rename a %s so that is hidden', (fileType) => {
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');

        const newName = '.hidden_file_or_folder';

        comp.renamingFile = ['', 'oldName', fileType];
        comp.repositoryFiles = { oldName: fileType };
        comp.onRenameFile(newName);
        fixture.detectChanges();

        expect(onErrorSpy).toHaveBeenCalledOnce();
        expect(onErrorSpy).toHaveBeenCalledWith('unsupportedFile');
    });

    it('should leave rename state if renaming a file to the same file name', fakeAsync(() => {
        const fileName = 'file1';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).not.toBeNull();

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileStub).not.toHaveBeenCalled();
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).toBeNull();
    }));

    it('should disable action buttons if there is a git conflict', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CONFLICT }));
        getRepositoryContentStub.mockReturnValue(of(repositoryContent));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.commitState).toEqual(CommitState.CONFLICT);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).toBeTrue();
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).toBeTrue();
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).toBeFalse();

        // Resolve conflict.
        conflictService.notifyConflictState(GitConflictState.OK);
        getStatusStub.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));

        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.commitState).toEqual(CommitState.CLEAN);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).toBeFalse();
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).toBeFalse();
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).toBeFalse();

        expect(getRepositoryContentStub).toHaveBeenCalledOnce();
        expect(comp.selectedFile).toBeUndefined();
    });

    it('should load information about changed files', fakeAsync(() => {
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
        };
        const getFilesWithChangeInfoStub = jest.fn().mockReturnValue(of(changeInformation));
        codeEditorRepositoryFileService.getFilesWithInformationAboutChange = getFilesWithChangeInfoStub;

        const loadFiles = comp.loadFilesWithInformationAboutChange().subscribe((result) => {
            expect(result).toEqual(filteredChangeInformation);
        });

        tick();

        expect(getFilesWithChangeInfoStub).toHaveBeenCalledOnce();

        loadFiles.unsubscribe();
    }));
});
