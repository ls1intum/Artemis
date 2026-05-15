import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { By } from '@angular/platform-browser';
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
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
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
    setupTestBed({ zoneless: true });

    let comp: CodeEditorActionsComponent;
    let fixture: ComponentFixture<CodeEditorActionsComponent>;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let updateFilesStub: ReturnType<typeof vi.spyOn>;
    let commitStub: ReturnType<typeof vi.spyOn>;
    let pullStub: ReturnType<typeof vi.spyOn>;
    let resetRepositoryStub: ReturnType<typeof vi.spyOn>;
    let dialogService: DialogService;
    let conflictStateService: CodeEditorConflictStateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), CodeEditorActionsComponent, TranslatePipeMock, FeatureToggleDirective],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: DialogService, useClass: MockDialogService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeEditorActionsComponent);
        comp = fixture.componentInstance;
        // unsavedFiles is now `input.required<>()` and editorState/commitState are `model.required<>()`.
        // Seed sensible defaults so tests that don't explicitly bind them don't trigger a
        // required-input validation error on first read.
        fixture.componentRef.setInput('unsavedFiles', {});
        fixture.componentRef.setInput('editorState', EditorState.CLEAN);
        fixture.componentRef.setInput('commitState', CommitState.UNDEFINED);
        codeEditorRepositoryFileService = TestBed.inject(CodeEditorRepositoryFileService);
        updateFilesStub = vi.spyOn(codeEditorRepositoryFileService, 'updateFiles');
        codeEditorRepositoryService = TestBed.inject(CodeEditorRepositoryService);
        commitStub = vi.spyOn(codeEditorRepositoryService, 'commit');
        pullStub = vi.spyOn(codeEditorRepositoryService, 'pull');
        resetRepositoryStub = vi.spyOn(codeEditorRepositoryService, 'resetRepository');
        dialogService = TestBed.inject(DialogService);
        conflictStateService = TestBed.inject(CodeEditorConflictStateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
            comp.editorState.set(editorState);
            comp.commitState.set(commitState);
            comp.isBuilding.set(isBuilding);
            fixture.detectChanges();
            const commitButton = fixture.debugElement.query(By.css('#submit_button'));
            const refreshButton = fixture.debugElement.query(By.css('#refresh_button'));

            expect(!commitButton.nativeElement.disabled).toEqual(enableCommitButton);
            expect(!refreshButton.nativeElement.disabled).toEqual(enableRefreshButton);
        });
    });

    it('should NOT update ui when building', () => {
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        comp.commitState.set(CommitState.COMMITTING);
        fixture.detectChanges();
        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        const commitButtonFeedbackBeforeStartBuild = commitButton.nativeElement.innerHTML;
        comp.isBuilding.set(true);
        fixture.detectChanges();
        const commitButtonFeedbackAfterStartBuild = commitButton.nativeElement.innerHTML;
        expect(commitButtonFeedbackAfterStartBuild).toEqual(commitButtonFeedbackBeforeStartBuild);
    });

    it('should call repositoryFileService to save unsavedFiles and emit result on success', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const onSavedFilesSpy = vi.spyOn(comp.onSavedFiles, 'emit');
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        comp.isBuilding.set(false);
        fixture.componentRef.setInput('unsavedFiles', unsavedFiles);
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        comp.onSave();

        // wait for save result
        expect(comp.editorState()).toEqual(EditorState.SAVING);

        fixture.detectChanges();

        // receive result for save
        saveObservable.next(savedFilesResult);
        expect(comp.editorState()).toEqual(EditorState.SAVING);
        expect(updateFilesStub).toHaveBeenNthCalledWith(1, [{ fileName: 'fileName', fileContent: unsavedFiles.fileName }], false);
        expect(onSavedFilesSpy).toHaveBeenCalledWith(savedFilesResult);

        fixture.detectChanges();
    });

    it('should call repositoryFileService to save unsavedFiles and emit an error on failure', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const errorResponse = { error: 'fatalError' };
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        const saveObservable = new Subject<typeof errorResponse>();
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        comp.isBuilding.set(false);
        fixture.componentRef.setInput('unsavedFiles', unsavedFiles);
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        comp.onSave();

        // waiting for save result
        expect(updateFilesStub).toHaveBeenNthCalledWith(1, [{ fileName: 'fileName', fileContent: unsavedFiles.fileName }], false);
        expect(comp.editorState()).toEqual(EditorState.SAVING);

        fixture.detectChanges();

        // receive error for save
        saveObservable.error(errorResponse);
        expect(onErrorSpy).toHaveBeenCalledWith('saveFailed');
        expect(comp.editorState()).toEqual(EditorState.UNSAVED_CHANGES);
        fixture.detectChanges();
    });

    it('should commit if no unsaved changes exist and update its state on response', () => {
        const commitObservable = new Subject<null>();
        comp.commitState.set(CommitState.UNCOMMITTED_CHANGES);
        comp.editorState.set(EditorState.CLEAN);
        comp.isBuilding.set(false);
        fixture.componentRef.setInput('unsavedFiles', {});
        fixture.detectChanges();

        commitStub.mockReturnValue(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).toBe(false);

        // start commit, wait for result
        commitButton.nativeElement.click();
        expect(commitStub).toHaveBeenNthCalledWith(1);
        expect(comp.isBuilding()).toBe(false);
        expect(comp.commitState()).toEqual(CommitState.COMMITTING);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBe(true);

        // commit result mockReturnValue
        commitObservable.next(null);
        expect(comp.isBuilding()).toBe(true);
        expect(comp.commitState()).toEqual(CommitState.CLEAN);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBe(false);
    });

    it('should commit if no unsaved changes exist and emit an error on error response', () => {
        const commitObservable = new Subject<void>();
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        comp.commitState.set(CommitState.UNCOMMITTED_CHANGES);
        comp.editorState.set(EditorState.CLEAN);
        comp.isBuilding.set(false);
        fixture.componentRef.setInput('unsavedFiles', {});
        fixture.detectChanges();

        commitStub.mockReturnValue(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).toBe(false);

        // start commit, wait for result
        commitButton.nativeElement.click();
        expect(commitStub).toHaveBeenNthCalledWith(1);
        expect(comp.isBuilding()).toBe(false);
        expect(comp.commitState()).toEqual(CommitState.COMMITTING);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBe(true);

        // commit result mockReturnValue an error
        commitObservable.error('error!');
        expect(comp.isBuilding()).toBe(false);
        expect(comp.commitState()).toEqual(CommitState.UNCOMMITTED_CHANGES);
        expect(onErrorSpy).toHaveBeenNthCalledWith(1, 'submitFailed');

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBe(false);
    });

    it('should emit different error messages on different error responses', () => {
        let commitObservable = new Subject<void>();
        const onErrorSpy = vi.spyOn(comp.onError, 'emit');
        comp.commitState.set(CommitState.UNCOMMITTED_CHANGES);
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

    it('should not commit if unsavedFiles exist, instead should save files with commit set to true', async () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const saveObservable = new Subject<null>();
        const saveChangedFilesStub = vi.spyOn(comp, 'saveChangedFiles');
        comp.commitState.set(CommitState.UNCOMMITTED_CHANGES);
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        comp.isBuilding.set(false);

        fixture.componentRef.setInput('unsavedFiles', unsavedFiles);
        fixture.detectChanges();

        saveChangedFilesStub.mockReturnValue(saveObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).toBe(false);

        // unsaved changes exist, needs to save files first
        commitButton.nativeElement.click();

        expect(commitStub).not.toHaveBeenCalled();
        expect(saveChangedFilesStub).toHaveBeenCalledOnce();
        expect(comp.commitState()).toEqual(CommitState.COMMITTING);

        // save + commit completed
        saveObservable.next(null);
        expect(comp.commitState()).toEqual(CommitState.COMMITTING);

        // Simulate that all files were saved — editorState transitions SAVING -> CLEAN.
        // The migrated component watches this transition via an effect (replacing the
        // legacy ngOnChanges flow) and reacts via a setTimeout(0) macrotask, so allow
        // the macrotask to settle before asserting.
        comp.editorState.set(EditorState.SAVING);
        fixture.detectChanges();
        comp.editorState.set(EditorState.CLEAN);
        fixture.detectChanges();
        await new Promise((resolve) => setTimeout(resolve, 0));

        expect(comp.isBuilding()).toBe(true);
        expect(comp.commitState()).toEqual(CommitState.CLEAN);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).toBe(false);

        fixture.destroy();
    });

    it.each([true, false])('should autosave unsaved files after 30 seconds if autosave is not disabled', async (disableAutoSave: boolean) => {
        vi.useFakeTimers();
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        comp.isBuilding.set(false);
        fixture.componentRef.setInput('unsavedFiles', unsavedFiles);
        fixture.componentRef.setInput('disableAutoSave', disableAutoSave);

        const saveChangedFilesSpy = vi.spyOn(comp, 'saveChangedFiles');
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        vi.advanceTimersByTime(1000 * 31);

        // receive result for save
        if (disableAutoSave) {
            expect(saveChangedFilesSpy).not.toHaveBeenCalled();
            expect(comp.editorState()).toEqual(EditorState.UNSAVED_CHANGES);
        } else {
            expect(saveChangedFilesSpy).toHaveBeenCalledOnce();
            expect(saveChangedFilesSpy).toHaveBeenCalledWith();
            saveObservable.next(savedFilesResult);
            expect(comp.editorState()).toEqual(EditorState.SAVING);
        }

        vi.useRealTimers();
        fixture.detectChanges();
        fixture.destroy();
    });

    it('should save on destroy', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        comp.isBuilding.set(false);
        fixture.componentRef.setInput('unsavedFiles', unsavedFiles);
        fixture.detectChanges();

        updateFilesStub.mockReturnValue(saveObservable);

        // receive result for save
        saveObservable.next(savedFilesResult);

        fixture.detectChanges();
        fixture.destroy();

        expect(comp.editorState()).toEqual(EditorState.SAVING);
    });

    it('should open refresh confirmation modal and execute refresh on confirmation', () => {
        const onClose = new Subject<boolean | undefined>();
        const openStub = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockImplementation(() => undefined);
        comp.editorState.set(EditorState.UNSAVED_CHANGES);

        comp.onRefresh();
        onClose.next(true);

        expect(openStub).toHaveBeenCalled();
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should not execute refresh if the refresh confirmation modal is dismissed', () => {
        const onClose = new Subject<boolean | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockImplementation(() => undefined);
        comp.editorState.set(EditorState.UNSAVED_CHANGES);

        comp.onRefresh();
        onClose.next(undefined);

        expect(executeRefreshStub).not.toHaveBeenCalled();
    });

    it('should execute refresh directly when editor is clean', () => {
        const openStub = vi.spyOn(dialogService, 'open');
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockImplementation(() => undefined);
        comp.editorState.set(EditorState.CLEAN);

        comp.onRefresh();

        expect(openStub).not.toHaveBeenCalled();
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should execute refresh and set clean state on successful pull', () => {
        const pullObservable = new Subject<void>();
        const refreshFilesEmitStub = vi.spyOn(comp.onRefreshFiles, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh();
        expect(comp.editorState()).toEqual(EditorState.REFRESHING);

        pullObservable.next();

        expect(refreshFilesEmitStub).toHaveBeenCalledOnce();
        expect(comp.editorState()).toEqual(EditorState.CLEAN);
    });

    it('should emit internet-disconnected refresh error on pull failure', () => {
        const pullObservable = new Subject<void>();
        const onErrorStub = vi.spyOn(comp.onError, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh();
        pullObservable.error(new ConnectionError());

        expect(comp.editorState()).toEqual(EditorState.UNSAVED_CHANGES);
        expect(onErrorStub).toHaveBeenCalledWith('refreshFailedInternetDisconnected');
    });

    it('should emit generic refresh error on pull failure', () => {
        const pullObservable = new Subject<void>();
        const onErrorStub = vi.spyOn(comp.onError, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh();
        pullObservable.error(new Error('something'));

        expect(comp.editorState()).toEqual(EditorState.UNSAVED_CHANGES);
        expect(onErrorStub).toHaveBeenCalledWith('refreshFailed');
    });

    it('should reset repository and refresh after modal confirmation', () => {
        const onClose = new Subject<boolean | undefined>();
        const resetObservable = new Subject<void>();
        const openStub = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockImplementation(() => undefined);
        const notifyConflictStateStub = vi.spyOn(conflictStateService, 'notifyConflictState');
        resetRepositoryStub.mockReturnValue(resetObservable);

        comp.resetRepository();
        onClose.next(true);
        resetObservable.next();

        expect(openStub).toHaveBeenCalled();
        expect(resetRepositoryStub).toHaveBeenCalledOnce();
        expect(notifyConflictStateStub).toHaveBeenCalledWith(GitConflictState.OK);
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should not reset repository when the modal is dismissed', () => {
        const onClose = new Subject<boolean | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);

        comp.resetRepository();
        onClose.next(undefined);

        expect(resetRepositoryStub).not.toHaveBeenCalled();
    });

    it('should emit reset error when repository reset fails', () => {
        const onClose = new Subject<boolean | undefined>();
        const resetObservable = new Subject<void>();
        const onErrorStub = vi.spyOn(comp.onError, 'emit');
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        resetRepositoryStub.mockReturnValue(resetObservable);

        comp.resetRepository();
        onClose.next(true);
        resetObservable.error(new Error('reset failed'));

        expect(onErrorStub).toHaveBeenCalledWith('resetFailed');
    });

    // Guard: when editorState transitions SAVING -> CLEAN while commitState is COMMITTING, the deferred
    // cascade finalizes the commit. The commitState guard is re-evaluated INSIDE the setTimeout, so a
    // concurrent move out of COMMITTING is respected and commitState is NOT clobbered.
    it('should re-read commitState inside the deferred cascade so a concurrent move out of COMMITTING is respected', async () => {
        comp.commitState.set(CommitState.COMMITTING);
        comp.editorState.set(EditorState.SAVING);
        fixture.detectChanges();

        // editorState transitions SAVING -> CLEAN (e.g. saveChangedFiles completed). This schedules a
        // setTimeout that will finalize the commit. Before the macrotask fires, the parent moves
        // commitState to UNDEFINED (e.g. file-browser raised a CHECKOUT_CONFLICT / reset).
        comp.editorState.set(EditorState.CLEAN);
        fixture.detectChanges();
        comp.commitState.set(CommitState.UNDEFINED);
        fixture.detectChanges();

        await new Promise((resolve) => setTimeout(resolve, 0));

        // The deferred branch must guard on the CURRENT commitState, not the value at scheduling time.
        // Therefore commitState stays UNDEFINED — NOT clobbered to CLEAN.
        expect(comp.commitState()).toEqual(CommitState.UNDEFINED);
    });
});
