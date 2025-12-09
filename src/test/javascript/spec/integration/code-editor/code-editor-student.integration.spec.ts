import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { DebugElement } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { CommitState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ResultService } from 'app/exercise/result/result.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockActivatedRouteWithSubjects } from 'test/helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockCodeEditorRepositoryService } from 'test/helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from 'test/helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorBuildLogService } from 'test/helpers/mocks/service/mock-code-editor-build-log.service';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { CodeEditorGridComponent } from 'app/programming/shared/code-editor/layout/code-editor-grid/code-editor-grid.component';
import { CodeEditorInstructionsComponent } from 'app/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { CodeEditorActionsComponent } from 'app/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserComponent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorBuildOutputComponent } from 'app/programming/manage/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/programming/manage/code-editor/file-browser/create-node/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/programming/manage/code-editor/file-browser/folder/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserFileComponent } from 'app/programming/manage/code-editor/file-browser/file/code-editor-file-browser-file.component';
import { CodeEditorStatusComponent } from 'app/programming/shared/code-editor/status/code-editor-status.component';
import { TreeViewComponent } from 'app/programming/shared/code-editor/treeview/components/tree-view/tree-view.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorMonacoComponent } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { mockCodeEditorMonacoViewChildren } from 'test/helpers/mocks/mock-instance.helper';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CodeEditorStudentIntegration', () => {
    let container: CodeEditorStudentContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorStudentContainerComponent>;
    let containerDebugElement: DebugElement;
    let codeEditorRepositoryService: CodeEditorRepositoryService;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let route: ActivatedRoute;

    let checkIfRepositoryIsCleanStub: jest.SpyInstance;
    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let getFeedbackDetailsForResultStub: jest.SpyInstance;
    let getStudentParticipationWithLatestResultStub: jest.SpyInstance;

    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | undefined>;
    let routeSubject: Subject<Params>;

    const result: Result = { id: 3, successful: false, completionDate: dayjs().subtract(2, 'days') };

    // Workaround for an error with MockComponent(). You can remove this once https://github.com/help-me-mom/ng-mocks/issues/8634 is resolved.
    mockCodeEditorMonacoViewChildren();

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                CodeEditorStudentContainerComponent,
                CodeEditorContainerComponent,
                MockComponent(CodeEditorFileBrowserComponent),
                MockComponent(CodeEditorInstructionsComponent),
                CodeEditorRepositoryIsLockedComponent,
                MockPipe(KeysPipe),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(UpdatingResultComponent),
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(AdditionalFeedbackComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CodeEditorGridComponent),
                MockComponent(CodeEditorActionsComponent),
                MockComponent(CodeEditorBuildOutputComponent),
                MockComponent(CodeEditorMonacoComponent),
                MockComponent(CodeEditorFileBrowserCreateNodeComponent),
                MockComponent(CodeEditorFileBrowserFolderComponent),
                MockComponent(CodeEditorFileBrowserFileComponent),
                MockComponent(CodeEditorStatusComponent),
                TreeViewComponent,
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                SessionStorageService,
                { provide: ResultService, useClass: MockResultService },
                LocalStorageService,
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                containerFixture = TestBed.createComponent(CodeEditorStudentContainerComponent);
                container = containerFixture.componentInstance;
                containerDebugElement = containerFixture.debugElement;

                codeEditorRepositoryService = TestBed.inject(CodeEditorRepositoryService);
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                resultService = TestBed.inject(ResultService);
                programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
                route = TestBed.inject(ActivatedRoute);

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);

                routeSubject = new Subject<Params>();
                // @ts-ignore
                (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

                checkIfRepositoryIsCleanStub = jest.spyOn(codeEditorRepositoryService, 'getStatus');
                subscribeForLatestResultOfParticipationStub = jest
                    .spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation')
                    .mockReturnValue(subscribeForLatestResultOfParticipationSubject);
                getFeedbackDetailsForResultStub = jest.spyOn(resultService, 'getFeedbackDetailsForResult');
                getStudentParticipationWithLatestResultStub = jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);
        subscribeForLatestResultOfParticipationStub.mockReturnValue(subscribeForLatestResultOfParticipationSubject);

        routeSubject = new Subject<Params>();
        // @ts-ignore
        (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);
    });

    it('should initialize correctly on route change if participation can be retrieved', () => {
        container.ngOnInit();
        const feedbacks = [{ id: 2 }] as Feedback[];
        result.feedbacks = feedbacks;
        const submission = { id: 1, results: [result] } as any;
        const participation = { id: 1, submissions: [submission], exercise: { id: 99 } } as Participation;
        const findWithLatestResultSubject = new Subject<Participation>();
        getStudentParticipationWithLatestResultStub.mockReturnValue(findWithLatestResultSubject);

        routeSubject.next({ participationId: 1 });

        expect(container.loadingParticipation).toBeTrue();

        findWithLatestResultSubject.next(participation);

        expect(getStudentParticipationWithLatestResultStub).toHaveBeenNthCalledWith(1, participation.id);
        expect(container.loadingParticipation).toBeFalse();
        expect(container.participationCouldNotBeFetched).toBeFalse();
        expect(container.participation).toEqual(Object.assign({}, participation, { submissions: [Object.assign({}, submission, { results: [result] })] }));
    });

    // TODO re-enable after remove-gitalb issues are resolved
    it.skip('should show the repository locked badge and disable the editor actions if the participation is locked', () => {
        container.ngOnInit();
        const participation = {
            id: 1,
            results: [result],
            exercise: { id: 99, dueDate: dayjs().subtract(2, 'hours') } as ProgrammingExercise,
            locked: true,
        } as any;
        const feedbacks = [{ id: 2 }] as Feedback[];
        const findWithLatestResultSubject = new Subject<Participation>();
        const getFeedbackDetailsForResultSubject = new Subject<{ body: Feedback[] }>();
        const isCleanSubject = new Subject();
        getStudentParticipationWithLatestResultStub.mockReturnValue(findWithLatestResultSubject);
        getFeedbackDetailsForResultStub.mockReturnValue(getFeedbackDetailsForResultSubject);
        checkIfRepositoryIsCleanStub.mockReturnValue(isCleanSubject);

        routeSubject.next({ participationId: 1 });
        findWithLatestResultSubject.next(participation);
        getFeedbackDetailsForResultSubject.next({ body: feedbacks });

        containerFixture.detectChanges();
        isCleanSubject.next({ repositoryStatus: CommitState.CLEAN });

        // Repository should be locked, the student can't write into it anymore.
        expect(container.repositoryIsLocked).toBeTrue();
        expect(getElement(containerDebugElement, '.locked-container').innerHTML).toContain('fa-icon');
        expect(container.codeEditorContainer.fileBrowser.disableActions).toBeTrue();
        expect(container.codeEditorContainer.actions.disableActions).toBeTrue();
    });

    it('should abort initialization and show error state if participation cannot be retrieved', () => {
        container.ngOnInit();
        const findWithLatestResultSubject = new Subject<{ body: Participation }>();
        getStudentParticipationWithLatestResultStub.mockReturnValue(findWithLatestResultSubject);

        routeSubject.next({ participationId: 1 });

        expect(container.loadingParticipation).toBeTrue();

        findWithLatestResultSubject.error('fatal error');

        expect(container.loadingParticipation).toBeFalse();
        expect(container.participationCouldNotBeFetched).toBeTrue();
        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(container.participation).toBeUndefined();
    });
});
