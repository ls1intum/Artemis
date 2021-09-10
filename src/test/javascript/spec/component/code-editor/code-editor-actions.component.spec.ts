import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DebugElement, SimpleChange } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SinonStub, spy, stub } from 'sinon';
import { Subject } from 'rxjs';
import { isEqual as _isEqual } from 'lodash';

import { AceEditorModule } from 'ng2-ace-editor';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ArtemisTestModule } from '../../test.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';

import { cartesianProduct } from 'app/shared/util/utils';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { MockCodeEditorConflictStateService } from '../../helpers/mocks/service/mock-code-editor-conflict-state.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { CommitState, EditorState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorActionsComponent', () => {
    let comp: CodeEditorActionsComponent;
    let fixture: ComponentFixture<CodeEditorActionsComponent>;
    let debugElement: DebugElement;
    let codeEditorRepositoryFileService: CodeEditorRepositoryFileService;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let updateFilesStub: SinonStub;
    let commitStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, AceEditorModule, FeatureToggleModule],
            declarations: [CodeEditorActionsComponent],
            providers: [
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorConflictStateService, useClass: MockCodeEditorConflictStateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorActionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                codeEditorRepositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);
                updateFilesStub = stub(codeEditorRepositoryFileService, 'updateFiles');
                codeEditorRepositoryService = debugElement.injector.get(CodeEditorRepositoryService);
                commitStub = stub(codeEditorRepositoryService, 'commit');
            });
    });

    afterEach(() => {
        updateFilesStub.restore();
        commitStub.restore();
    });

    it('should show refresh and submit button without any inputs', () => {
        fixture.detectChanges();
        const submitButton = fixture.debugElement.query(By.css('#submit_button'));
        const refreshButton = fixture.debugElement.query(By.css('#refresh_button'));
        expect(submitButton).to.exist;
        expect(refreshButton).to.exist;
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

            expect(!commitButton.nativeElement.disabled).to.equal(enableCommitButton);
            expect(!refreshButton.nativeElement.disabled).to.equal(enableRefreshButton);
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
        expect(commitButtonFeedbackAfterStartBuild).to.be.equal(commitButtonFeedbackBeforeStartBuild);
    });

    it('should call repositoryFileService to save unsavedFiles and emit result on success', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const onSavedFilesSpy = spy(comp.onSavedFiles, 'emit');
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.returns(saveObservable);

        comp.onSave();

        // wait for save result
        expect(comp.editorState).to.be.equal(EditorState.SAVING);

        fixture.detectChanges();

        // receive result for save
        saveObservable.next(savedFilesResult);
        expect(comp.editorState).to.be.equal(EditorState.SAVING);
        expect(updateFilesStub).to.have.been.calledOnceWithExactly([{ fileName: 'fileName', fileContent: unsavedFiles.fileName }], false);
        expect(onSavedFilesSpy).to.have.been.calledOnceWith(savedFilesResult);

        fixture.detectChanges();
    });

    it('should call repositoryFileService to save unsavedFiles and emit an error on failure', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const errorResponse = { error: 'fatalError' };
        const onErrorSpy = spy(comp.onError, 'emit');
        const saveObservable = new Subject<typeof errorResponse>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.returns(saveObservable);

        comp.onSave();

        // waiting for save result
        expect(updateFilesStub).to.have.been.calledOnceWithExactly([{ fileName: 'fileName', fileContent: unsavedFiles.fileName }], false);
        expect(comp.editorState).to.be.equal(EditorState.SAVING);

        fixture.detectChanges();

        // receive error for save
        saveObservable.error(errorResponse);
        expect(onErrorSpy).to.have.been.calledOnceWith('saveFailed');
        expect(comp.editorState).to.be.equal(EditorState.UNSAVED_CHANGES);
        fixture.detectChanges();
    });

    it('should commit if no unsaved changes exist and update its state on response', () => {
        const commitObservable = new Subject<null>();
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.editorState = EditorState.CLEAN;
        comp.isBuilding = false;
        comp.unsavedFiles = {};
        fixture.detectChanges();

        commitStub.returns(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).to.be.false;

        // start commit, wait for result
        commitButton.nativeElement.click();
        expect(commitStub).to.have.been.calledOnceWithExactly();
        expect(comp.isBuilding).to.be.false;
        expect(comp.commitState).to.equal(CommitState.COMMITTING);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).to.be.true;

        // commit result returns
        commitObservable.next(null);
        expect(comp.isBuilding).to.be.true;
        expect(comp.commitState).to.equal(CommitState.CLEAN);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).to.be.false;
    });

    it('should commit if no unsaved changes exist and emit an error on error response', () => {
        const commitObservable = new Subject<void>();
        const onErrorSpy = spy(comp.onError, 'emit');
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.editorState = EditorState.CLEAN;
        comp.isBuilding = false;
        comp.unsavedFiles = {};
        fixture.detectChanges();

        commitStub.returns(commitObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).to.be.false;

        // start commit, wait for result
        commitButton.nativeElement.click();
        expect(commitStub).to.have.been.calledOnceWithExactly();
        expect(comp.isBuilding).to.be.false;
        expect(comp.commitState).to.equal(CommitState.COMMITTING);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).to.be.true;

        // commit result returns an error
        commitObservable.error('error!');
        expect(comp.isBuilding).to.be.false;
        expect(comp.commitState).to.equal(CommitState.UNCOMMITTED_CHANGES);
        expect(onErrorSpy).to.have.been.calledOnceWithExactly('commitFailed');

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).to.be.false;
    });

    it('should not commit if unsavedFiles exist, instead should save files with commit set to true', fakeAsync(() => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const saveObservable = new Subject<null>();
        const saveChangedFilesStub = stub(comp, 'saveChangedFiles');
        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;

        comp.unsavedFiles = unsavedFiles;
        comp.saveChangedFiles = saveChangedFilesStub;
        fixture.detectChanges();

        saveChangedFilesStub.returns(saveObservable);

        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(commitButton.nativeElement.disabled).to.be.false;

        // unsaved changes exist, needs to save files first
        commitButton.nativeElement.click();

        expect(commitStub).to.not.have.been.called;
        expect(saveChangedFilesStub).to.have.been.calledOnce;
        expect(comp.commitState).to.equal(CommitState.COMMITTING);

        // save + commit completed
        saveObservable.next(null);
        expect(comp.commitState).to.equal(CommitState.COMMITTING);

        // Simulate that all files were saved
        comp.ngOnChanges({
            editorState: new SimpleChange(EditorState.SAVING, EditorState.CLEAN, true),
        });

        tick();

        expect(comp.isBuilding).to.be.true;
        expect(comp.commitState).to.equal(CommitState.CLEAN);

        fixture.detectChanges();
        expect(commitButton.nativeElement.disabled).to.be.false;

        fixture.destroy();
        flush();
    }));

    it('should autosave unsaved files after 30 seconds', fakeAsync(() => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.returns(saveObservable);

        tick(1000 * 31);

        // receive result for save
        saveObservable.next(savedFilesResult);
        expect(comp.editorState).to.be.equal(EditorState.SAVING);

        fixture.detectChanges();
        fixture.destroy();
        flush();
    }));

    it('should save on destroy', () => {
        const unsavedFiles = { fileName: 'lorem ipsum fileContent lorem ipsum' };
        const savedFilesResult: { [fileName: string]: null } = { fileName: null };
        const saveObservable = new Subject<typeof savedFilesResult>();
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        comp.unsavedFiles = unsavedFiles;
        fixture.detectChanges();

        updateFilesStub.returns(saveObservable);

        // receive result for save
        saveObservable.next(savedFilesResult);

        fixture.detectChanges();
        fixture.destroy();

        expect(comp.editorState).to.be.equal(EditorState.SAVING);
    });
});
