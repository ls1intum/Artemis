import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as ace from 'brace';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { problemStatement } from '../../helpers/sample/problemStatement.json';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import {
    CodeEditorBuildLogService,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
} from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Result } from 'app/entities/result.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockCodeEditorRepositoryService } from '../../helpers/mocks/service/mock-code-editor-repository.service';
import { MockCodeEditorBuildLogService } from '../../helpers/mocks/service/mock-code-editor-build-log.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockExerciseHintService } from '../../helpers/mocks/service/mock-exercise-hint.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../helpers/mocks/service/mock-websocket.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-exercise-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { ExerciseHintStudentComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorInstructorIntegration', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist');
    let container: CodeEditorInstructorAndEditorContainerComponent;
    let containerFixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let containerDebugElement: DebugElement;
    let domainService: DomainService;
    let route: ActivatedRoute;

    let checkIfRepositoryIsCleanStub: SinonStub;
    let getRepositoryContentStub: SinonStub;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getFeedbackDetailsForResultStub: SinonStub;
    let getBuildLogsStub: SinonStub;
    let findWithParticipationsStub: SinonStub;
    let getLatestResultWithFeedbacksStub: SinonStub;
    let getHintsForExerciseStub: SinonStub;

    let checkIfRepositoryIsCleanSubject: Subject<{ isClean: boolean }>;
    let getRepositoryContentSubject: Subject<{ [fileName: string]: FileType }>;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | null>;
    let findWithParticipationsSubject: Subject<{ body: ProgrammingExercise }>;
    let routeSubject: Subject<Params>;

    const exerciseHints = [{ id: 1 }, { id: 2 }];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [
                CodeEditorInstructorAndEditorContainerComponent,
                CodeEditorContainerComponent,
                KeysPipe,
                CodeEditorInstructionsComponent,
                MockComponent(CodeEditorGridComponent),
                MockComponent(CodeEditorActionsComponent),
                MockComponent(CodeEditorFileBrowserComponent),
                MockComponent(CodeEditorAceComponent),
                CodeEditorBuildOutputComponent,
                MockPipe(ArtemisDatePipe),
                MockComponent(AlertComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                ProgrammingExerciseInstructorExerciseStatusComponent,
                UpdatingResultComponent,
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockComponent(ExerciseHintStudentComponent),
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(MarkdownEditorComponent),
                ProgrammingExerciseInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionAnalysisComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(ResultComponent),
                MockComponent(ProgrammingExerciseInstructionStepWizardComponent),
            ],
            providers: [
                JhiLanguageHelper,
                ChangeDetectorRef,
                DeviceDetectorService,
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: CodeEditorRepositoryService, useClass: MockCodeEditorRepositoryService },
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorBuildLogService, useClass: MockCodeEditorBuildLogService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                containerFixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
                container = containerFixture.componentInstance;
                containerDebugElement = containerFixture.debugElement;

                const codeEditorRepositoryService = containerDebugElement.injector.get(CodeEditorRepositoryService);
                const codeEditorRepositoryFileService = containerDebugElement.injector.get(CodeEditorRepositoryFileService);
                const participationWebsocketService = containerDebugElement.injector.get(ParticipationWebsocketService);
                const resultService = containerDebugElement.injector.get(ResultService);
                const buildLogService = containerDebugElement.injector.get(CodeEditorBuildLogService);
                const programmingExerciseParticipationService = containerDebugElement.injector.get(ProgrammingExerciseParticipationService);
                const programmingExerciseService = containerDebugElement.injector.get(ProgrammingExerciseService);
                domainService = containerDebugElement.injector.get(DomainService);
                route = containerDebugElement.injector.get(ActivatedRoute);
                const exerciseHintService = containerDebugElement.injector.get(ExerciseHintService);
                containerDebugElement.injector.get(Router);

                checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
                getRepositoryContentSubject = new Subject<{ [fileName: string]: FileType }>();
                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
                findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();

                routeSubject = new Subject<Params>();
                // @ts-ignore
                (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

                checkIfRepositoryIsCleanStub = stub(codeEditorRepositoryService, 'getStatus');
                getRepositoryContentStub = stub(codeEditorRepositoryFileService, 'getRepositoryContent');
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult');
                getLatestResultWithFeedbacksStub = stub(programmingExerciseParticipationService, 'getLatestResultWithFeedback').returns(throwError('no result'));
                getBuildLogsStub = stub(buildLogService, 'getBuildLogs');
                getHintsForExerciseStub = stub(exerciseHintService, 'findByExerciseId').returns(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);

                findWithParticipationsStub = stub(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndResults');
                findWithParticipationsStub.returns(findWithParticipationsSubject);

                subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);
                getRepositoryContentStub.returns(getRepositoryContentSubject);
                checkIfRepositoryIsCleanStub.returns(checkIfRepositoryIsCleanSubject);
            });
    });

    afterEach(() => {
        checkIfRepositoryIsCleanStub.restore();
        getRepositoryContentStub.restore();
        subscribeForLatestResultOfParticipationStub.restore();
        getFeedbackDetailsForResultStub.restore();
        getBuildLogsStub.restore();
        findWithParticipationsStub.restore();
        getLatestResultWithFeedbacksStub.restore();

        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
        subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);

        routeSubject = new Subject<Params>();
        // @ts-ignore
        (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

        findWithParticipationsSubject = new Subject<{ body: ProgrammingExercise }>();
        findWithParticipationsStub.returns(findWithParticipationsSubject);

        checkIfRepositoryIsCleanSubject = new Subject<{ isClean: boolean }>();
        checkIfRepositoryIsCleanStub.returns(checkIfRepositoryIsCleanSubject);

        getRepositoryContentSubject = new Subject<{ [p: string]: FileType }>();
        getRepositoryContentStub.returns(getRepositoryContentSubject);
    });

    const initContainer = (exercise: ProgrammingExercise) => {
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1 });
        expect(container.codeEditorContainer).to.be.undefined;
        expect(findWithParticipationsStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(container.loadingState).to.equal(container.LOADING_STATE.INITIALIZING);
    };

    it('should load the exercise and select the template participation if no participation id is provided', () => {
        jest.resetModules();
        // @ts-ignore
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2, repositoryUrl: 'test' }],
            templateParticipation: { id: 3, repositoryUrl: 'test2', results: [{ id: 9, submission: { id: 1, buildFailed: false } }] },
            solutionParticipation: { id: 4, repositoryUrl: 'test3' },
            course: { id: 1 },
        } as ProgrammingExercise;
        exercise.studentParticipations = exercise.studentParticipations?.map((p) => {
            p.exercise = exercise;
            return p;
        });
        exercise.templateParticipation = { ...exercise.templateParticipation, programmingExercise: exercise };
        exercise.solutionParticipation = { ...exercise.solutionParticipation, programmingExercise: exercise };

        getFeedbackDetailsForResultStub.returns(of([]));
        const setDomainSpy = spy(domainService, 'setDomain');
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1');
        initContainer(exercise);

        findWithParticipationsSubject.next({ body: exercise });

        expect(getLatestResultWithFeedbacksStub).not.to.have.been.called;
        expect(setDomainSpy).to.have.been.calledOnce;
        expect(setDomainSpy).to.have.been.calledOnceWithExactly([DomainType.PARTICIPATION, exercise.templateParticipation]);
        expect(container.exercise).to.deep.equal(exercise);
        expect(container.selectedRepository).to.equal(container.REPOSITORY.TEMPLATE);
        expect(container.selectedParticipation).to.deep.equal(container.selectedParticipation);
        expect(container.loadingState).to.equal(container.LOADING_STATE.CLEAR);
        expect(container.domainChangeSubscription).to.exist;

        containerFixture.detectChanges();
        expect(container.codeEditorContainer.grid).to.exist;

        checkIfRepositoryIsCleanSubject.next({ isClean: true });
        getRepositoryContentSubject.next({ file: FileType.FILE, folder: FileType.FOLDER });
        containerFixture.detectChanges();

        // Submission could be built
        expect(getBuildLogsStub).to.not.have.been.called;
        // Once called by each build-output & instructions
        expect(getFeedbackDetailsForResultStub).to.have.been.calledTwice;

        expect(container.codeEditorContainer.grid).to.exist;
        expect(container.codeEditorContainer.fileBrowser).to.exist;
        expect(container.codeEditorContainer.actions).to.exist;
        expect(container.editableInstructions).to.exist;
        expect(container.editableInstructions.participation.id).to.deep.equal(exercise.templateParticipation.id);
        expect(container.resultComp).to.exist;
        expect(container.codeEditorContainer.buildOutput).to.exist;
        expect(container.editableInstructions.exerciseHints).to.deep.equal(exerciseHints);

        // Called once by each build-output, instructions, result and twice by instructor-exercise-status (=templateParticipation,solutionParticipation) &
        expect(subscribeForLatestResultOfParticipationStub.callCount).to.equal(5);

        // called once by instructions (hints are only visible if assignment repo is selected).
        expect(getHintsForExerciseStub).to.have.been.calledOnce;
        expect(getHintsForExerciseStub).to.have.been.calledWithExactly(exercise.id);
    });

    it('should go into error state when loading the exercise failed', () => {
        const exercise = { id: 1, studentParticipations: [{ id: 2 }], templateParticipation: { id: 3 }, solutionParticipation: { id: 4 } } as ProgrammingExercise;
        const setDomainSpy = spy(domainService, 'setDomain');
        initContainer(exercise);

        findWithParticipationsSubject.error('fatal error');

        expect(setDomainSpy).to.not.have.been.called;
        expect(container.loadingState).to.equal(container.LOADING_STATE.FETCHING_FAILED);
        expect(container.selectedRepository).to.be.undefined;

        containerFixture.detectChanges();
        expect(container.codeEditorContainer).to.be.undefined;
    });

    it('should load test repository if specified in url', () => {
        const exercise = {
            id: 1,
            problemStatement,
            studentParticipations: [{ id: 2 }],
            templateParticipation: { id: 3 },
            solutionParticipation: { id: 4 },
            course: { id: 1 },
        } as ProgrammingExercise;
        const setDomainSpy = spy(domainService, 'setDomain');
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/test');
        container.ngOnDestroy();
        initContainer(exercise);

        findWithParticipationsSubject.next({ body: exercise });

        expect(setDomainSpy).to.have.been.calledOnceWithExactly([DomainType.TEST_REPOSITORY, exercise]);
        expect(container.selectedParticipation).not.to.exist;
        expect(container.selectedRepository).to.equal(container.REPOSITORY.TEST);
        expect(getBuildLogsStub).not.to.have.been.called;
        expect(getFeedbackDetailsForResultStub).not.to.have.been.called;

        containerFixture.detectChanges();

        expect(container.codeEditorContainer).to.exist;
        expect(container.editableInstructions).to.exist;
        expect(container.editableInstructions.participation).to.deep.equal(exercise.templateParticipation);
        expect(container.resultComp).not.to.exist;
        expect(container.codeEditorContainer.buildOutput).not.to.exist;
    });

    const checkSolutionRepository = (exercise: ProgrammingExercise) => {
        expect(container.selectedRepository).to.equal(container.REPOSITORY.SOLUTION);
        expect(container.selectedParticipation).to.deep.equal(exercise.solutionParticipation);
        expect(container.codeEditorContainer).to.exist;
        expect(container.editableInstructions).to.exist;
        expect(container.resultComp).to.exist;
        expect(container.codeEditorContainer.buildOutput).to.exist;
        expect(container.codeEditorContainer.buildOutput.participation).to.deep.equal(exercise.solutionParticipation);
        expect(container.editableInstructions.participation).to.deep.equal(exercise.solutionParticipation);
    };

    it('should be able to switch between the repos and update the child components accordingly', () => {
        // @ts-ignore
        const exercise = {
            id: 1,
            course: { id: 1 },
            problemStatement,
        } as ProgrammingExercise;
        exercise.templateParticipation = { id: 3, repositoryUrl: 'test2', programmingExercise: exercise } as TemplateProgrammingExerciseParticipation;
        exercise.solutionParticipation = { id: 4, repositoryUrl: 'test3', programmingExercise: exercise } as SolutionProgrammingExerciseParticipation;
        // @ts-ignore
        exercise.studentParticipations = [{ id: 2, repositoryUrl: 'test', exercise } as ProgrammingExerciseStudentParticipation];

        const setDomainSpy = spy(domainService, 'setDomain');

        // Start with assignment repository
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/2');
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1, participationId: 2 });
        findWithParticipationsSubject.next({ body: exercise });

        containerFixture.detectChanges();

        expect(container.selectedRepository).to.equal(container.REPOSITORY.ASSIGNMENT);
        expect(container.selectedParticipation).to.deep.equal(exercise.studentParticipations[0]);
        expect(container.codeEditorContainer).to.exist;
        expect(container.editableInstructions).to.exist;
        expect(container.resultComp).to.exist;
        expect(container.codeEditorContainer.buildOutput).to.exist;
        expect(container.codeEditorContainer.buildOutput.participation).to.deep.equal(exercise.studentParticipations[0]);
        expect(container.editableInstructions.participation).to.deep.equal(exercise.studentParticipations[0]);

        // New select solution repository
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/4');
        routeSubject.next({ exerciseId: 1, participationId: 4 });

        containerFixture.detectChanges();

        checkSolutionRepository(exercise);

        expect(findWithParticipationsStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(setDomainSpy).to.have.been.calledTwice;
        expect(setDomainSpy).to.have.been.calledWith([DomainType.PARTICIPATION, exercise.studentParticipations[0]]);
        expect(setDomainSpy).to.have.been.calledWith([DomainType.PARTICIPATION, exercise.solutionParticipation]);
    });

    it('should not be able to select a repository without repositoryUrl', () => {
        // @ts-ignore
        const exercise = {
            id: 1,
            course: { id: 1 },
            problemStatement,
        } as ProgrammingExercise;
        // @ts-ignore
        exercise.studentParticipations = [{ id: 2, repositoryUrl: 'test', exercise } as ProgrammingExerciseStudentParticipation];
        exercise.templateParticipation = { id: 3, programmingExercise: exercise } as TemplateProgrammingExerciseParticipation;
        exercise.solutionParticipation = { id: 4, repositoryUrl: 'test3', programmingExercise: exercise } as SolutionProgrammingExerciseParticipation;

        const setDomainSpy = spy(domainService, 'setDomain');

        // Start with assignment repository
        // @ts-ignore
        (container.router as MockRouter).setUrl('code-editor-instructor/1/3');
        container.ngOnInit();
        routeSubject.next({ exerciseId: 1, participationId: 3 });
        findWithParticipationsSubject.next({ body: exercise });

        containerFixture.detectChanges();

        expect(setDomainSpy).to.have.been.calledOnce;
        expect(setDomainSpy).to.have.been.calledOnceWithExactly([DomainType.PARTICIPATION, exercise.solutionParticipation]);
        checkSolutionRepository(exercise);
    });
});
