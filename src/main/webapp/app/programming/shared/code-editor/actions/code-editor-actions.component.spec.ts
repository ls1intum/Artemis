import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { By } from '@angular/platform-browser';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Observable, Subject, of } from 'rxjs';
import { isEqual as _isEqual } from 'lodash-es';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService, ConnectionError } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorActionsComponent } from 'app/programming/shared/code-editor/actions/code-editor-actions.component';
import { MockCodeEditorConflictStateService } from 'test/helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockCodeEditorRepositoryFileService } from 'test/helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorRepositoryService } from 'test/helpers/mocks/service/mock-code-editor-repository.service';
import { CommitState, EditorState, FileSubmission, GitConflictState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockModule } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
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
        vi.useRealTimers();
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

    it('should not report a file as saved when its content changed during the request', () => {
        const saveObservable = new Subject<{ fileName: null }>();
        const onSavedFilesSpy = vi.spyOn(comp.onSavedFiles, 'emit');
        comp.editorState.set(EditorState.UNSAVED_CHANGES);
        fixture.componentRef.setInput('unsavedFiles', { fileName: 'content being saved' });
        fixture.detectChanges();
        updateFilesStub.mockReturnValue(saveObservable);

        comp.onSave();
        fixture.componentRef.setInput('unsavedFiles', { fileName: 'newer collaborative edit' });
        fixture.detectChanges();
        saveObservable.next({ fileName: null });

        expect(onSavedFilesSpy).not.toHaveBeenCalled();
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

        saveChangedFilesStub.mockReturnValue(saveObservable as unknown as Observable<FileSubmission | undefined>);

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

        // editorState SAVING -> CLEAN drives a setTimeout(0) commit completion; flush the macrotask.
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

    describe('when actions are disabled', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('disableActions', true);
            fixture.componentRef.setInput('unsavedFiles', { fileName: 'dirty content' });
            comp.editorState.set(EditorState.UNSAVED_CHANGES);
            comp.commitState.set(CommitState.UNCOMMITTED_CHANGES);
        });

        it('should not save dirty files directly', () => {
            const nextSpy = vi.fn();
            const onSavedFilesSpy = vi.spyOn(comp.onSavedFiles, 'emit');

            comp.saveChangedFiles(true).subscribe(nextSpy);

            expect(updateFilesStub).not.toHaveBeenCalled();
            expect(nextSpy).not.toHaveBeenCalled();
            expect(onSavedFilesSpy).not.toHaveBeenCalled();
            expect(comp.editorState()).toBe(EditorState.UNSAVED_CHANGES);
        });

        it('should not save dirty files through onSave', () => {
            const saveChangedFilesSpy = vi.spyOn(comp, 'saveChangedFiles');

            comp.onSave();

            expect(saveChangedFilesSpy).not.toHaveBeenCalled();
            expect(updateFilesStub).not.toHaveBeenCalled();
        });

        it('should not autosave dirty files', () => {
            vi.useFakeTimers();
            const saveChangedFilesSpy = vi.spyOn(comp, 'saveChangedFiles');
            fixture.detectChanges();

            vi.advanceTimersByTime(1000 * 31);

            expect(saveChangedFilesSpy).not.toHaveBeenCalled();
            expect(updateFilesStub).not.toHaveBeenCalled();
            expect(comp.editorState()).toBe(EditorState.UNSAVED_CHANGES);
            vi.useRealTimers();
        });

        it('should not save dirty files on destroy', () => {
            const saveChangedFilesSpy = vi.spyOn(comp, 'saveChangedFiles');
            fixture.detectChanges();

            fixture.destroy();

            expect(saveChangedFilesSpy).not.toHaveBeenCalled();
            expect(updateFilesStub).not.toHaveBeenCalled();
            expect(comp.editorState()).toBe(EditorState.UNSAVED_CHANGES);
        });

        it.each([
            ['dirty files', { fileName: 'dirty content' }],
            ['a clean working tree', {}],
        ])('should not submit with %s', (_description, unsavedFiles) => {
            const onCommitSpy = vi.spyOn(comp.onCommit, 'emit');
            fixture.componentRef.setInput('unsavedFiles', unsavedFiles);

            comp.commit();

            expect(updateFilesStub).not.toHaveBeenCalled();
            expect(commitStub).not.toHaveBeenCalled();
            expect(onCommitSpy).not.toHaveBeenCalled();
            expect(comp.commitState()).toBe(CommitState.UNCOMMITTED_CHANGES);
            expect(comp.isBuilding()).toBe(false);
        });

        it('should not open the reset dialog or mutate the repository', () => {
            const openStub = vi.spyOn(dialogService, 'open');

            comp.resetRepository();

            expect(openStub).not.toHaveBeenCalled();
            expect(resetRepositoryStub).not.toHaveBeenCalled();
        });

        it('should not refresh dirty files through user actions', () => {
            const openStub = vi.spyOn(dialogService, 'open');
            const refreshResult = vi.fn();

            comp.onRefresh();
            comp.executeRefresh().subscribe(refreshResult);

            expect(openStub).not.toHaveBeenCalled();
            expect(pullStub).not.toHaveBeenCalled();
            expect(refreshResult).not.toHaveBeenCalled();
            expect(comp.editorState()).toBe(EditorState.UNSAVED_CHANGES);
        });
    });

    it('should not reset the repository if actions become disabled while the confirmation dialog is open', () => {
        const onClose = new Subject<boolean | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);

        comp.resetRepository();
        fixture.componentRef.setInput('disableActions', true);
        onClose.next(true);

        expect(resetRepositoryStub).not.toHaveBeenCalled();
    });

    it('should not refresh if actions become disabled while the confirmation dialog is open', () => {
        const onClose = new Subject<boolean | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockReturnValue(of(false));
        comp.editorState.set(EditorState.UNSAVED_CHANGES);

        comp.onRefresh();
        fixture.componentRef.setInput('disableActions', true);
        onClose.next(true);

        expect(executeRefreshStub).not.toHaveBeenCalled();
        expect(pullStub).not.toHaveBeenCalled();
        expect(comp.editorState()).toBe(EditorState.UNSAVED_CHANGES);
    });

    it('should open refresh confirmation modal and execute refresh on confirmation', () => {
        const onClose = new Subject<boolean | undefined>();
        const openStub = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockReturnValue(of(true));
        comp.editorState.set(EditorState.UNSAVED_CHANGES);

        comp.onRefresh();
        onClose.next(true);

        expect(openStub).toHaveBeenCalled();
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should not execute refresh if the refresh confirmation modal is dismissed', () => {
        const onClose = new Subject<boolean | undefined>();
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockReturnValue(of(false));
        comp.editorState.set(EditorState.UNSAVED_CHANGES);

        comp.onRefresh();
        onClose.next(undefined);

        expect(executeRefreshStub).not.toHaveBeenCalled();
    });

    it('should execute refresh directly when editor is clean', () => {
        const openStub = vi.spyOn(dialogService, 'open');
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockReturnValue(of(true));
        comp.editorState.set(EditorState.CLEAN);

        comp.onRefresh();

        expect(openStub).not.toHaveBeenCalled();
        expect(executeRefreshStub).toHaveBeenCalledOnce();
    });

    it('should execute refresh and set clean state on successful pull', () => {
        const pullObservable = new Subject<void>();
        const refreshFilesEmitStub = vi.spyOn(comp.onRefreshFiles, 'emit');
        const refreshResult = vi.fn();
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh().subscribe(refreshResult);
        expect(comp.editorState()).toEqual(EditorState.REFRESHING);

        pullObservable.next();

        expect(refreshFilesEmitStub).toHaveBeenCalledOnce();
        expect(comp.editorState()).toEqual(EditorState.CLEAN);
        expect(refreshResult).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should emit internet-disconnected refresh error on pull failure', () => {
        const pullObservable = new Subject<void>();
        const onErrorStub = vi.spyOn(comp.onError, 'emit');
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh().subscribe();
        pullObservable.error(new ConnectionError());

        expect(comp.editorState()).toEqual(EditorState.UNSAVED_CHANGES);
        expect(onErrorStub).toHaveBeenCalledWith('refreshFailedInternetDisconnected');
    });

    it('should emit generic refresh error on pull failure', () => {
        const pullObservable = new Subject<void>();
        const onErrorStub = vi.spyOn(comp.onError, 'emit');
        const refreshResult = vi.fn();
        pullStub.mockReturnValue(pullObservable);

        comp.executeRefresh().subscribe(refreshResult);
        pullObservable.error(new Error('something'));

        expect(comp.editorState()).toEqual(EditorState.UNSAVED_CHANGES);
        expect(onErrorStub).toHaveBeenCalledWith('refreshFailed');
        expect(refreshResult).toHaveBeenCalledExactlyOnceWith(false);
    });

    it('should reset repository and refresh after modal confirmation', () => {
        const onClose = new Subject<boolean | undefined>();
        const resetObservable = new Subject<void>();
        const openStub = vi.spyOn(dialogService, 'open').mockReturnValue({ onClose, close: vi.fn() } as any);
        const executeRefreshStub = vi.spyOn(comp, 'executeRefresh').mockReturnValue(of(true));
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
