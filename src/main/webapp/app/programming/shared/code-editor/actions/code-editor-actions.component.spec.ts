import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { By } from '@angular/platform-browser';
import { SimpleChange } from '@angular/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject } from 'rxjs';
import { isEqual as _isEqual } from 'lodash-es';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService, ConnectionError } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorActionsComponent } from 'app/programming/shared/code-editor/actions/code-editor-actions.component';
import { MockCodeEditorConflictStateService } from 'test/helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockCodeEditorRepositoryFileService } from 'test/helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorRepositoryService } from 'test/helpers/mocks/service/mock-code-editor-repository.service';
import { CommitState, EditorState, GitConflictState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockModule } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { NgbModal, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

// Cartesian product helper function
const cartesianConcatHelper = (a: any[], b: any[]): any[][] => ([] as any[][]).concat(...a.map((a2) => b.map((b2) => ([] as any[]).concat(a2, b2))));

/**
 * Returns the cartesian product for all arrays provided to the function.
 * Type of the arrays does not matter, it will just return the combinations without any type information.
 * Implementation taken from here: https://gist.github.com/ssippe/1f92625532eef28be6974f898efb23ef.
 * @param a an array
 * @param b another array
 * @param c rest of arrays
 */
const cartesianProduct = (a: any[], b: any[], ...c: any[][]): any[] => {
    if (!b || b.length === 0) {
        return a;
    }
    const [b2, ...c2] = c;
    const fab = cartesianConcatHelper(a, b);
    return cartesianProduct(fab, b2, ...c2);
};

describe('CodeEditorActionsComponent', () => {
    let comp: CodeEditorActionsComponent;
    let fixture: ComponentFixture<CodeEditorActionsComponent>;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let updateFilesStub: jest.SpyInstance;
    let commitStub: jest.SpyInstance;
    let pullStub: jest.SpyInstance;
    let resetRepositoryStub: jest.SpyInstance;
    let modalService: NgbModal;
    let conflictStateService: CodeEditorConflictStateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [CodeEditorActionsComponent, TranslatePipeMock, FeatureToggleDirective],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorActionsComponent);
                comp = fixture.componentInstance;
                codeEditorRepositoryFileService = TestBed.inject(CodeEditorRepositoryFileService);
                updateFilesStub = jest.spyOn(codeEditorRepositoryFileService, 'updateFiles');
                codeEditorRepositoryService = TestBed.inject(CodeEditorRepositoryService);
                commitStub = jest.spyOn(codeEditorRepositoryService, 'commit');
                pullStub = jest.spyOn(codeEditorRepositoryService, 'pull');
                resetRepositoryStub = jest.spyOn(codeEditorRepositoryService, 'resetRepository');
                modalService = TestBed.inject(NgbModal);
                conflictStateService = TestBed.inject(CodeEditorConflictStateService);
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

    it('should open refresh confirmation modal and execute refresh on confirmation', () => {
        const shouldRefresh = new Subject<void>();
        const openStub = jest.spyOn(modalService, 'open').mockReturnValue({
            componentInstance: { shouldRefresh },
        } as any);
        const executeRefreshStub = jest.spyOn(comp, 'executeRefresh').mockImplementation();
        comp.editorState = EditorState.UNSAVED_CHANGES;

        comp.onRefresh();
        shouldRefresh.next();

        expect(openStub).toHaveBeenCalled();
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should execute refresh directly when editor is clean', () => {
        const openStub = jest.spyOn(modalService, 'open');
        const executeRefreshStub = jest.spyOn(comp, 'executeRefresh').mockImplementation();
        comp.editorState = EditorState.CLEAN;

        comp.onRefresh();

        expect(openStub).not.toHaveBeenCalled();
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should execute refresh and set clean state on successful pull', () => {
        const pullObservable = new Subject<void>();
        const refreshFilesEmitStub = jest.spyOn(comp.onRefreshFiles, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh();
        expect(comp.editorState).toEqual(EditorState.REFRESHING);

        pullObservable.next();

        expect(refreshFilesEmitStub).toHaveBeenCalledOnce();
        expect(comp.editorState).toEqual(EditorState.CLEAN);
    });

    it('should emit internet-disconnected refresh error on pull failure', () => {
        const pullObservable = new Subject<void>();
        const onErrorStub = jest.spyOn(comp.onError, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh();
        pullObservable.error(new ConnectionError());

        expect(comp.editorState).toEqual(EditorState.UNSAVED_CHANGES);
        expect(onErrorStub).toHaveBeenCalledWith('refreshFailedInternetDisconnected');
    });

    it('should emit generic refresh error on pull failure', () => {
        const pullObservable = new Subject<void>();
        const onErrorStub = jest.spyOn(comp.onError, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh();
        pullObservable.error(new Error('something'));

        expect(comp.editorState).toEqual(EditorState.UNSAVED_CHANGES);
        expect(onErrorStub).toHaveBeenCalledWith('refreshFailed');
    });

    it('should reset repository and refresh after modal confirmation', () => {
        const shouldReset = new Subject<void>();
        const resetObservable = new Subject<void>();
        const openStub = jest.spyOn(modalService, 'open').mockReturnValue({
            componentInstance: { shouldReset },
        } as any);
        const executeRefreshStub = jest.spyOn(comp, 'executeRefresh').mockImplementation();
        const notifyConflictStateStub = jest.spyOn(conflictStateService, 'notifyConflictState');
        resetRepositoryStub.mockReturnValue(resetObservable);

        comp.resetRepository();
        shouldReset.next();
        resetObservable.next();

        expect(openStub).toHaveBeenCalled();
        expect(resetRepositoryStub).toHaveBeenCalledOnce();
        expect(notifyConflictStateStub).toHaveBeenCalledWith(GitConflictState.OK);
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should emit reset error when repository reset fails', () => {
        const shouldReset = new Subject<void>();
        const resetObservable = new Subject<void>();
        const onErrorStub = jest.spyOn(comp.onError, 'emit');
        jest.spyOn(modalService, 'open').mockReturnValue({
            componentInstance: { shouldReset },
        } as any);
        resetRepositoryStub.mockReturnValue(resetObservable);

        comp.resetRepository();
        shouldReset.next();
        resetObservable.error(new Error('reset failed'));

        expect(onErrorStub).toHaveBeenCalledWith('resetFailed');
    });
});
