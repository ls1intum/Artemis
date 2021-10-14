import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockPipe } from 'ng-mocks';
import { CookieService } from 'ngx-cookie-service';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { TreeviewItem, TreeviewModule } from 'ngx-treeview';
import { of, Subject } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CommitState, FileType, GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { DeviceDetectorService } from 'ngx-device-detector';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorConflictStateService } from '../../helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CodeEditorFileBrowserComponent', () => {
    let comp: CodeEditorFileBrowserComponent;
    let fixture: ComponentFixture<CodeEditorFileBrowserComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let conflictService: CodeEditorConflictStateService;
    let getRepositoryContentMock: jest.SpyInstance;
    let getStatusMock: jest.SpyInstance;
    let createFileMock: jest.SpyInstance;
    let renameFileMock: jest.SpyInstance;

    const createFileRoot = '#create_file_root';
    const createFolderRoot = '#create_folder_root';
    const compressTree = '#compress_tree';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TreeviewModule.forRoot()],
            declarations: [
                CodeEditorFileBrowserComponent,
                MockComponent(CodeEditorStatusComponent),
                CodeEditorFileBrowserFileComponent,
                CodeEditorFileBrowserFolderComponent,
                CodeEditorFileBrowserCreateNodeComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                CodeEditorFileService,
                DeviceDetectorService,
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
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
                getStatusMock = jest.spyOn(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentMock = jest.spyOn(codeEditorRepositoryFileService, 'getRepositoryContent');
                createFileMock = jest.spyOn(codeEditorRepositoryFileService, 'createFile').mockReturnValue(of(undefined));
                renameFileMock = jest.spyOn(codeEditorRepositoryFileService, 'renameFile').mockReturnValue(of(undefined));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create no treeviewItems if getRepositoryContent returns an empty result', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        const expectedFileTreeItems: TreeviewItem[] = [];
        getRepositoryContentMock.mockReturnValue(of(repositoryContent));
        getStatusMock.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toEqual(repositoryContent);
        expect(comp.filesTreeViewItem).toEqual(expectedFileTreeItems);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(0);
        expect(renderedFiles.length).toBe(0);
    });

    it('should create treeviewItems if getRepositoryContent returns files', () => {
        const repositoryContent: { [fileName: string]: string } = { file: 'FILE', folder: 'FOLDER' };
        getRepositoryContentMock.mockReturnValue(of(repositoryContent));
        getStatusMock.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toEqual(repositoryContent);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(1);
        expect(renderedFiles.length).toBe(1);
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
        comp.setupTreeview();
        fixture.detectChanges();
        const folder = comp.filesTreeViewItem.find(({ value }) => value === 'folder')!;
        expect(folder).toBeDefined();
        expect(folder.children.length).toBe(1);
        const file1 = folder.children.find(({ value }) => value === 'folder/file1')!;
        expect(file1).toBeDefined();
        expect(file1.children).toBeUndefined();
        const folder2 = comp.filesTreeViewItem.find(({ value }) => value === 'folder2')!;
        expect(folder2).toBeDefined();
        expect(folder2.children.length).toBe(2);
        const file2 = folder2.children.find(({ value }) => value === 'folder2/file2')!;
        expect(file2).toBeDefined();
        expect(file2.children).toBeUndefined();
        const folder3 = folder2.children.find(({ value }) => value === 'folder2/folder3')!;
        expect(folder3).toBeDefined();
        expect(folder3.children.length).toBe(1);
        const file3 = folder3.children.find(({ value }) => value === 'folder2/folder3/file3')!;
        expect(file3).toBeDefined();
        expect(file3.children).toBeUndefined();
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(3);
        expect(renderedFiles.length).toBe(3);
    });

    it('should filter forbidden files from getRepositoryContent response', () => {
        const allowedFiles = {
            'allowedFile.java': FileType.FILE,
        };
        const forbiddenFiles = {
            'danger.bin': FileType.FOLDER,
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
        getRepositoryContentMock.mockReturnValue(of(repositoryContent));
        getStatusMock.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toEqual(allowedFiles);
        expect(comp.filesTreeViewItem.map((x) => x.toString())).toEqual(expectedFileTreeItems);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(0);
        expect(renderedFiles.length).toBe(1);
    });

    it('should not load files if commitState could not be retrieved (possibly corrupt repository or server error)', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        const loadFilesSpy = jest.spyOn(comp, 'loadFiles');
        getStatusMock.mockReturnValue(isCleanSubject);
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(true);
        expect(comp.commitState).toEqual(CommitState.UNDEFINED);
        isCleanSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.commitState).toEqual(CommitState.COULD_NOT_BE_RETRIEVED);
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toBeUndefined();
        expect(comp.filesTreeViewItem).toBeUndefined();
        expect(onErrorSpy).toHaveBeenCalledTimes(1);
        expect(loadFilesSpy).toHaveBeenCalledTimes(0);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(0);
        expect(renderedFiles.length).toBe(0);
    });

    it('should set isLoading false and emit an error if loadFiles fails', () => {
        const isCleanSubject = new Subject<{ isClean: boolean }>();
        const getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        getStatusMock.mockReturnValue(isCleanSubject);
        getRepositoryContentMock.mockReturnValue(getRepositoryContentSubject);
        comp.commitState = CommitState.UNDEFINED;
        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(true);
        expect(comp.commitState).toEqual(CommitState.UNDEFINED);
        isCleanSubject.next({ isClean: true });
        getRepositoryContentSubject.error('fatal error');

        fixture.detectChanges();
        expect(comp.isLoadingFiles).toBe(false);
        expect(comp.repositoryFiles).toBeUndefined();
        expect(comp.filesTreeViewItem).toBeUndefined();
        expect(onErrorSpy).toHaveBeenCalledTimes(1);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(0);
        expect(renderedFiles.length).toBe(0);
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
        expect(selectedTreeItem.checked).toBe(true);
        const renderedFolders = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        const renderedFiles = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(renderedFolders.length).toBe(2);
        expect(renderedFiles.length).toBe(2);

        const selectedFileHtml = renderedFiles[0];
        const isSelected = !!selectedFileHtml.query(By.css('.node-selected'));
        expect(isSelected).toBe(true);
        const notSelectedFilesHtml = [renderedFiles[1], ...renderedFolders];
        const areUnSelected = !notSelectedFilesHtml.some((el) => !!el.query(By.css('.node-selected')));
        expect(areUnSelected).toBe(true);
    });

    it('should add file to node tree if created', fakeAsync(() => {
        const fileName = 'file2';
        const filePath = 'folder2/file2';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem({
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
        expect(creatingElement).toBeDefined();
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).toBeDefined();

        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).toEqual(focusedElement);

        comp.onCreateFile(fileName);
        fixture.detectChanges();
        tick();

        expect(createFileMock).toHaveBeenCalledTimes(1);
        expect(createFileMock).toHaveBeenCalledWith(filePath);
        expect(comp.creatingFile).toBeUndefined();
        expect(setupTreeviewSpy).toHaveBeenCalledTimes(1);
        expect(setupTreeviewSpy).toHaveBeenCalledWith();
        expect(onFileChangeSpy).toHaveBeenCalledTimes(1);
        expect(comp.repositoryFiles).toEqual({ ...repositoryFiles, [filePath]: FileType.FILE });
        creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).toBeNull();
    }));

    it('should add folder to node tree if created', fakeAsync(() => {
        const fileName = 'folder3';
        const filePath = 'folder2/folder3';
        const repositoryFiles = { file1: FileType.FILE, folder2: FileType.FOLDER };
        const treeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder2',
                value: 'folder2',
            } as any),
        ];
        const onFileChangeSpy = jest.spyOn(comp.onFileChange, 'emit');
        const setupTreeviewSpy = jest.spyOn(comp, 'setupTreeview');
        let createFolderMock = jest.spyOn(codeEditorRepositoryFileService, 'createFolder').mockReturnValue(of(undefined));
        comp.repositoryFiles = repositoryFiles;
        comp.filesTreeViewItem = treeItems;
        comp.creatingFile = ['folder2', FileType.FOLDER];
        fixture.detectChanges();

        let creatingElement = debugElement.query(By.css('jhi-code-editor-file-browser-create-node'));
        expect(creatingElement).toBeDefined();
        const creatingInput = creatingElement.query(By.css('input'));
        expect(creatingInput).toBeDefined();

        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(creatingInput.nativeElement).toEqual(focusedElement);

        comp.onCreateFile(fileName);
        fixture.detectChanges();
        tick();

        expect(createFolderMock).toHaveBeenCalledTimes(1);
        expect(createFolderMock).toHaveBeenCalledWith(filePath);
        expect(comp.creatingFile).toBeUndefined();
        expect(setupTreeviewSpy).toHaveBeenCalledTimes(1);
        expect(setupTreeviewSpy).toHaveBeenCalledWith();
        expect(onFileChangeSpy).toHaveBeenCalledTimes(1);
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
        expect(onErrorSpy).toHaveBeenCalledTimes(1);
        expect(createFileMock).toHaveBeenCalledTimes(0);
    });

    it('should not be able to create node that already exists', () => {
        const fileName = 'file1';
        const repositoryFiles = { 'folder2/file1': FileType.FILE, folder2: FileType.FOLDER };
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        comp.creatingFile = ['folder2', FileType.FILE];
        comp.repositoryFiles = repositoryFiles;
        comp.onCreateFile(fileName);
        fixture.detectChanges();
        expect(onErrorSpy).toHaveBeenCalledTimes(1);
        expect(createFileMock).toHaveBeenCalledTimes(0);
    });

    it('should update repository file entry on rename', fakeAsync(() => {
        const fileName = 'file1';
        const afterRename = 'newFileName';
        const treeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'file1',
            } as any),
            new TreeviewItem({
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
        expect(filesInTreeHtml.length).toBe(1);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml.length).toBe(1);
        let renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeDefined();

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileMock).toHaveBeenCalledTimes(1);
        expect(renameFileMock).toHaveBeenCalledWith(fileName, afterRename);
        expect(comp.renamingFile).toBeUndefined();
        expect(onFileChangeSpy).toHaveBeenCalledTimes(1);
        expect(comp.repositoryFiles).toEqual({ folder2: FileType.FOLDER, [afterRename]: FileType.FILE });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml.length).toBe(1);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml.length).toBe(1);
        renamingInput = filesInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeNull();

        // Wait for focus of input element
        tick();
    }));

    it('should rename all paths concerned if a folder is renamed', fakeAsync(() => {
        const folderName = 'folder';
        const afterRename = 'newFolderName';
        const treeItems = [
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file1',
                value: 'folder/file1',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'file2',
                value: 'folder/file2',
            } as any),
            new TreeviewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder',
                value: 'folder',
            } as any),
            new TreeviewItem({
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
        expect(filesInTreeHtml.length).toBe(2);
        let foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml.length).toBe(2);
        let renamingInput = foldersInTreeHtml[0].query(By.css('input'));
        expect(renamingInput).toBeDefined();

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));

        expect(renameFileMock).toHaveBeenCalledTimes(1);
        expect(renameFileMock).toHaveBeenCalledWith(folderName, afterRename);
        expect(comp.renamingFile).toBeUndefined();
        expect(onFileChangeSpy).toHaveBeenCalledTimes(1);
        expect(comp.repositoryFiles).toEqual({
            [[afterRename, 'file1'].join('/')]: FileType.FILE,
            [[afterRename, 'file2'].join('/')]: FileType.FILE,
            [afterRename]: FileType.FOLDER,
            folder2: FileType.FOLDER,
        });

        filesInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'));
        expect(filesInTreeHtml.length).toBe(2);
        foldersInTreeHtml = debugElement.queryAll(By.css('jhi-code-editor-file-browser-folder'));
        expect(foldersInTreeHtml.length).toBe(2);
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
        expect(renamingInput).toBeDefined();

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileMock).toHaveBeenCalledTimes(0);
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).toBeDefined();
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
        expect(renamingInput).toBeDefined();

        // Wait for focus of input element
        tick();
        let focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.value = afterRename;
        renamingInput.nativeElement.dispatchEvent(new Event('input'));
        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileMock).toHaveBeenCalledTimes(0);
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).toBeDefined();
        focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);
    }));

    it('should leave rename state if renaming a file to the same file name', fakeAsync(() => {
        const fileName = 'file1';
        const repositoryFiles = { file1: FileType.FILE, newFileName: FileType.FILE };
        comp.repositoryFiles = repositoryFiles;
        comp.setupTreeview();
        fixture.detectChanges();
        comp.renamingFile = [fileName, fileName, FileType.FILE];
        fixture.detectChanges();

        let renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).toBeDefined();

        // Wait for focus of input element
        tick();
        const focusedElement = debugElement.query(By.css(':focus')).nativeElement;
        expect(renamingInput.nativeElement).toEqual(focusedElement);

        renamingInput.nativeElement.dispatchEvent(new Event('focusout'));
        fixture.detectChanges();

        expect(renameFileMock).toHaveBeenCalledTimes(0);
        expect(comp.repositoryFiles).toEqual(repositoryFiles);

        // When renaming failed, the input should not be closed, because the user probably still wants to rename
        renamingInput = debugElement.queryAll(By.css('jhi-code-editor-file-browser-file'))[0].query(By.css('input'));
        expect(renamingInput).toBeNull();
    }));

    it('should disable action buttons if there is a git conflict', () => {
        const repositoryContent: { [fileName: string]: string } = {};
        getStatusMock.mockReturnValue(of({ repositoryStatus: CommitState.CONFLICT }));
        getRepositoryContentMock.mockReturnValue(of(repositoryContent));
        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.commitState).toEqual(CommitState.CONFLICT);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).toBe(true);
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).toBe(true);
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).toBe(false);

        // Resolve conflict.
        conflictService.notifyConflictState(GitConflictState.OK);
        getStatusMock.mockReturnValue(of({ repositoryStatus: CommitState.CLEAN }));

        comp.commitState = CommitState.UNDEFINED;

        triggerChanges(comp, { property: 'commitState', currentValue: CommitState.UNDEFINED });
        fixture.detectChanges();

        expect(comp.commitState).toEqual(CommitState.CLEAN);

        expect(debugElement.query(By.css(createFileRoot)).nativeElement.disabled).toBe(false);
        expect(debugElement.query(By.css(createFolderRoot)).nativeElement.disabled).toBe(false);
        expect(debugElement.query(By.css(compressTree)).nativeElement.disabled).toBe(false);

        expect(getRepositoryContentMock).toHaveBeenCalledTimes(1);
        expect(comp.selectedFile).toBeUndefined();
    });
});
