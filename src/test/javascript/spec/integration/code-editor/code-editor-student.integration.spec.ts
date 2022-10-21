import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Feedback } from 'app/entities/feedback.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/participate/code-editor-student-container.component';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { TreeviewComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview/treeview.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import * as ace from 'brace';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Subject } from 'rxjs';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockCodeEditorBuildLogService } from '../../helpers/mocks/service/mock-code-editor-build-log.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { MockProgrammingSubmissionService } from '../../helpers/mocks/service/mock-programming-submission.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { getElement } from '../../helpers/utils/general.utils';
import { ArtemisTestModule } from '../../test.module';

describe('CodeEditorStudentIntegration', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist');
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

    const result = { id: 3, successful: false, completionDate: dayjs().subtract(2, 'days') };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
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
                MockDirective(NgbTooltip),
                MockComponent(CodeEditorGridComponent),
                MockComponent(CodeEditorActionsComponent),
                MockComponent(CodeEditorBuildOutputComponent),
                MockComponent(CodeEditorAceComponent),
                MockComponent(CodeEditorFileBrowserCreateNodeComponent),
                MockComponent(CodeEditorFileBrowserFolderComponent),
                MockComponent(CodeEditorFileBrowserFileComponent),
                MockComponent(CodeEditorStatusComponent),
                TreeviewComponent,
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
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
        const participation = { id: 1, results: [result], exercise: { id: 99 } } as Participation;
        const feedbacks = [{ id: 2 }] as Feedback[];
        const findWithLatestResultSubject = new Subject<Participation>();
        const getFeedbackDetailsForResultSubject = new Subject<{ body: Feedback[] }>();
        getStudentParticipationWithLatestResultStub.mockReturnValue(findWithLatestResultSubject);
        getFeedbackDetailsForResultStub.mockReturnValue(getFeedbackDetailsForResultSubject);

        routeSubject.next({ participationId: 1 });

        expect(container.loadingParticipation).toBeTrue();

        findWithLatestResultSubject.next(participation);
        getFeedbackDetailsForResultSubject.next({ body: feedbacks });

        expect(getStudentParticipationWithLatestResultStub).toHaveBeenNthCalledWith(1, participation.id);
        expect(getFeedbackDetailsForResultStub).toHaveBeenNthCalledWith(1, participation.id, result.id);
        expect(container.loadingParticipation).toBeFalse();
        expect(container.participationCouldNotBeFetched).toBeFalse();
        expect(container.participation).toEqual({ ...participation, results: [{ ...result, feedbacks }] });
    });

    it('should show the repository locked badge and disable the editor actions if the exercises buildAndTestAfterDueDate is set and the due date has passed', () => {
        container.ngOnInit();
        const participation = {
            id: 1,
            results: [result],
            exercise: { id: 99, buildAndTestStudentSubmissionsAfterDueDate: dayjs().subtract(1, 'hours'), dueDate: dayjs().subtract(2, 'hours') } as ProgrammingExercise,
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
