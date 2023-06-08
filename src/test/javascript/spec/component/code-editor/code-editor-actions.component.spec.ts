import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { DebugElement, SimpleChange } from '@angular/core';
import { Subject } from 'rxjs';
import { isEqual as _isEqual } from 'lodash-es';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ArtemisTestModule } from '../../test.module';
import { cartesianProduct } from 'app/shared/util/utils';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { MockCodeEditorConflictStateService } from '../../helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { CommitState, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { MockModule } from 'ng-mocks';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('CodeEditorActionsComponent', () => {
    let comp: CodeEditorActionsComponent;
    let fixture: ComponentFixture<CodeEditorActionsComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let updateFilesStub: jest.SpyInstance;
    let commitStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(AceEditorModule), MockModule(NgbTooltipModule)],
            declarations: [CodeEditorActionsComponent, TranslatePipeMock, FeatureToggleDirective],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorActionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                updateFilesStub = jest.spyOn(codeEditorRepositoryFileService, 'updateFiles');
                codeEditorRepositoryService = debugElement.injector.get(CodeEditorRepositoryService);
                commitStub = jest.spyOn(codeEditorRepositoryService, 'commit');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show refresh and submit button without any inputs', () => {
        fixture.detectChanges();
        const submitButton = fixture.debugElement.query(By.css('#submit_button'));
        const refreshButton = fixture.debugElement.query(By.css('#refresh_button'));
        expect(submitButton).not.toBeNull();
        expect(refreshButton).not.toBeNull();
    });

    const enableCommitButtonCombinations = cartesianProduct([EditorState.UNSAVED_CHANGES, EditorState.CLEAN], [CommitState.UNCOMMITTED_CHANGES, CommitState.CLEAN], [false, true]);
    const enableRefreshButtonCombinations = cartesianProduct(
        [EditorState.CLEAN, EditorState.UNSAVED_CHANGES],
        [CommitState.COULD_NOT_BE_RETRIEVED, CommitState.CLEAN, CommitState.UNCOMMITTED_CHANGES, CommitState.UNDEFINED],
        [false, true],
    );

    cartesianProduct(
        Object.keys(EditorState),
        Object.keys(CommitState).filter((commitState) => commitState !== CommitState.CONFLICT),
        [true, false],
    ).map((combination: [EditorState, CommitState, boolean]) => {
        const enableCommitButton = enableCommitButtonCombinations.some((c: [EditorState, CommitState, boolean]) => _isEqual(combination, c));
        const enableRefreshButton = enableRefreshButtonCombinations.some((c: [EditorState, CommitState, boolean]) => _isEqual(combination, c));
        return it(`Should
            ${enableCommitButton ? 'Enable commit button' : 'Disable commit button'} and
            ${enableRefreshButton ? 'Enable refresh buttton' : 'Disable refresh button'}
            for this state combination: EditorState.${combination[0]} / CommitState.${combination[1]} / ${combination[2] ? 'is building' : 'is not building'}
        `, () => {
            const [editorState, commitState, isBuilding] = combination;
            comp.editorState = editorState;
            comp.commitState = commitState;
            comp.isBuilding = isBuilding;
            fixture.detectChanges();
            const commitButton = fixture.debugElement.query(By.css('#submit_button'));
            const refreshButton = fixture.debugElement.query(By.css('#refresh_button'));

            expect(!commitButton.nativeElement.disabled).toEqual(enableCommitButton);
            expect(!refreshButton.nativeElement.disabled).toEqual(enableRefreshButton);
        });
    });

    it('should NOT update ui when building', () => {
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.commitState = CommitState.COMMITTING;
        fixture.detectChanges();
        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        const commitButtonFeedbackBeforeStartBuild = commitButton.nativeElement.innerHTML;
        comp.isBuilding = true;
        fixture.detectChanges();
        const commitButtonFeedbackAfterStartBuild = commitButton.nativeElement.innerHTML;
        expect(commitButtonFeedbackAfterStartBuild).toEqual(commitButtonFeedbackBeforeStartBuild);
    });

    it('should call repositoryFileService to save unsavedFiles and emit result on success', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const onSavedFilesSpy = jest.spyOn(comp.onSavedFiles, 'emit');
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        comp.onSave();

        // wait for save result
        expect(comp.editorState).toEqual(EditorState.SAVING);

        fixture.detectChanges();

        // receive result for save
        saveObservable.next(savedFilesResult);
        expect(comp.editorState).toEqual(EditorState.SAVING);
        expect(updateFilesStub).toHaveBeenNthCalledWith(1, [{ fileName: 'fileName', fileContent: unsavedFiles.fileName }], false);
        expect(onSavedFilesSpy).toHaveBeenCalledWith(savedFilesResult);

        fixture.detectChanges();
    });

    it('should call repositoryFileService to save unsavedFiles and emit an error on failure', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const errorResponse = { error: 'fatalError' };
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        const saveObservable = new Subject<typeof errorResponse>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        comp.onSave();

        // waiting for save result
        expect(updateFilesStub).toHaveBeenNthCalledWith(1, [{ fileName: 'fileName', fileContent: unsavedFiles.fileName }], false);
        expect(comp.editorState).toEqual(EditorState.SAVING);

        fixture.detectChanges();

        // receive error for save
        saveObservable.error(errorResponse);
        expect(onErrorSpy).toHaveBeenCalledWith('saveFailed');
        expect(comp.editorState).toEqual(EditorState.UNSAVED_CHANGES);
        fixture.detectChanges();
    });

    it('should commit if no unsaved changes exist and update its state on response', () => {
        const commitObservable = new Subject<null>();
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.editorState = EditorState.CLEAN;
        comp.isBuilding = false;
        comp.unsavedFiles = {};
        fixture.detectChanges();

        commitStub.mockReturnValue(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).toBeFalse();

        // start commit, wait for result
        commitButton.nativeElement.click();
        expect(commitStub).toHaveBeenNthCalledWith(1);
        expect(comp.isBuilding).toBeFalse();
        expect(comp.commitState).toEqual(CommitState.COMMITTING);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBeTrue();

        // commit result mockReturnValue
        commitObservable.next(null);
        expect(comp.isBuilding).toBeTrue();
        expect(comp.commitState).toEqual(CommitState.CLEAN);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBeFalse();
    });

    it('should commit if no unsaved changes exist and emit an error on error response', () => {
        const commitObservable = new Subject<void>();
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.editorState = EditorState.CLEAN;
        comp.isBuilding = false;
        comp.unsavedFiles = {};
        fixture.detectChanges();

        commitStub.mockReturnValue(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).toBeFalse();

        // start commit, wait for result
        commitButton.nativeElement.click();
        expect(commitStub).toHaveBeenNthCalledWith(1);
        expect(comp.isBuilding).toBeFalse();
        expect(comp.commitState).toEqual(CommitState.COMMITTING);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBeTrue();

        // commit result mockReturnValue an error
        commitObservable.error('error!');
        expect(comp.isBuilding).toBeFalse();
        expect(comp.commitState).toEqual(CommitState.UNCOMMITTED_CHANGES);
        expect(onErrorSpy).toHaveBeenNthCalledWith(1, 'submitFailed');

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBeFalse();
    });

    it('should emit different error messages on different error responses', () => {
        let commitObservable = new Subject<void>();
        const onErrorSpy = jest.spyOn(comp.onError, 'emit');
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        fixture.detectChanges();

        commitStub.mockReturnValue(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));

        commitButton.nativeElement.click();
        commitObservable.error({ error: { detail: 'submitBeforeStartDate' } });
        expect(onErrorSpy).toHaveBeenNthCalledWith(1, 'submitFailed');
        expect(onErrorSpy).toHaveBeenNthCalledWith(2, 'submitBeforeStartDate');

        commitObservable = new Subject<void>();
        commitStub.mockReturnValue(commitObservable);
        commitButton.nativeElement.click();
        commitObservable.error({ error: { detail: 'submitAfterDueDate' } });
        expect(onErrorSpy).toHaveBeenNthCalledWith(4, 'submitAfterDueDate');

        commitObservable = new Subject<void>();
        commitStub.mockReturnValue(commitObservable);
        commitButton.nativeElement.click();
        commitObservable.error({ error: { detail: 'submitAfterReachingSubmissionLimit' } });
        expect(onErrorSpy).toHaveBeenNthCalledWith(6, 'submitAfterReachingSubmissionLimit');
    });

    it('should not commit if unsavedFiles exist, instead should save files with commit set to true', fakeAsync(() => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const saveObservable = new Subject<null>();
        const saveChangedFilesStub = jest.spyOn(comp, 'saveChangedFiles');
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;

        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        saveChangedFilesStub.mockReturnValue(saveObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).toBeFalse();

        // unsaved changes exist, needs to save files first
        commitButton.nativeElement.click();

        expect(commitStub).not.toHaveBeenCalled();
        expect(saveChangedFilesStub).toHaveBeenCalledOnce();
        expect(comp.commitState).toEqual(CommitState.COMMITTING);

        // save + commit completed
        saveObservable.next(null);
        expect(comp.commitState).toEqual(CommitState.COMMITTING);

        // Simulate that all files were saved
        comp.ngOnChanges({
            editorState: new SimpleChange(EditorState.SAVING, EditorState.CLEAN, true),
        });

        tick();

        expect(comp.isBuilding).toBeTrue();
        expect(comp.commitState).toEqual(CommitState.CLEAN);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBeFalse();

        fixture.destroy();
        flush();
    }));

    it.each([true, false])(
        'should autosave unsaved files after 30 seconds if autosave is not disabled',
        fakeAsync((disableAutoSave: boolean) => {
            const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
            const savedFilesResult: { [fileName: string]: null } = { fileName: null };
            const saveObservable = new Subject<typeof savedFilesResult>();
            comp.editorState = EditorState.UNSAVED_CHANGES;
            comp.isBuilding = false;
            comp.unsavedFiles = unsavedFiles;
            comp.disableAutoSave = disableAutoSave;

            const saveChangedFilesSpy = jest.spyOn(comp, 'saveChangedFiles');
            fixture.detectChanges();

            updateFilesStub.mockReturnValue(saveObservable);

            tick(1000 * 31);

            // receive result for save
            if (disableAutoSave) {
                expect(saveChangedFilesSpy).not.toHaveBeenCalled();
                expect(comp.editorState).toEqual(EditorState.UNSAVED_CHANGES);
            } else {
                expect(saveChangedFilesSpy).toHaveBeenCalledOnce();
                expect(saveChangedFilesSpy).toHaveBeenCalledWith();
                saveObservable.next(savedFilesResult);
                expect(comp.editorState).toEqual(EditorState.SAVING);
            }

            fixture.detectChanges();
            fixture.destroy();
            flush();
        }),
    );

    it('should save on destroy', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        // receive result for save
        saveObservable.next(savedFilesResult);

        fixture.detectChanges();
        fixture.destroy();

        expect(comp.editorState).toEqual(EditorState.SAVING);
    });
});
