import { AlertService } from 'app/shared/service/alert.service';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { fakeAsync, tick } from '@angular/core/testing';

describe('CodeEditorInstructorAndEditorContainerComponent', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let alertService: AlertService;
    let translateService: TranslateService;

    const course = { id: 123, exercises: [] } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 123;
    const error1 = new ConsistencyCheckError();
    error1.programmingExercise = programmingExercise;
    error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;

    const mockIssues: ConsistencyIssue[] = [
        {
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.ConstructorParameterMismatch,
            description: 'Problem statement inconsistency',
            suggestedFix: 'Review the problem statement file.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.ProblemStatement,
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 42,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.MethodParameterMismatch,
            description: 'Template repository issue',
            suggestedFix: 'Fix template repository references.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'src/template/Example.java',
                    startLine: 5,
                    endLine: 50,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            description: 'Solution repository issue',
            suggestedFix: 'Fix solution repository references.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'src/solution/Solution.java',
                    startLine: 3,
                    endLine: 60,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Low,
            category: ConsistencyIssue.CategoryEnum.IdentifierNamingInconsistency,
            description: 'Tests repository issue',
            suggestedFix: 'Adjust tests in test repository.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/tests/ExampleTest.java',
                    startLine: 10,
                    endLine: 70,
                },
            ],
        },
        {
            // A multi-location issue for testing next/previous navigation
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.VisibilityMismatch,
            description: 'Multi-location navigation test issue',
            suggestedFix: 'Resolve inconsistencies across artifacts.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/template/A.java',
                    startLine: 10,
                    endLine: 20,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/template/B.java',
                    startLine: 30,
                    endLine: 40,
                },
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'src/template/C.java',
                    startLine: 50,
                    endLine: 60,
                },
            ],
        },
    ];

    beforeEach(waitForAsync(async () => {
        await TestBed.configureTestingModule({
            providers: [
                MockProvider(ArtemisIntelligenceService),
                MockProvider(ConsistencyCheckService),
                MockProvider(ProfileService),
                MockProvider(AlertService),
                MockProvider(ParticipationService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(CourseExerciseService),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            declarations: [],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);
        alertService = TestBed.inject(AlertService);
        translateService = TestBed.inject(TranslateService);

        (comp as any).codeEditorContainer = {
            selectedFile: undefined as string | undefined,
            selectedRepository: jest.fn().mockReturnValue('SOLUTION'),
            problemStatementIdentifier: 'problem_statement.md',
            jumpToLine: jest.fn(),
        };

        (comp as any).editableInstructions = {
            jumpToLine: jest.fn(),
        };

        comp.selectTemplateParticipation = jest.fn().mockResolvedValue(undefined);
        comp.selectSolutionParticipation = jest.fn().mockResolvedValue(undefined);
        comp.selectTestRepository = jest.fn().mockResolvedValue(undefined);
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('runs full consistency check and shows success when no issues', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const successSpy = jest.spyOn(alertService, 'success');

        comp.checkConsistencies(programmingExercise);
        expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(123);
        expect(artemisIntelligenceService.consistencyCheck).toHaveBeenCalledWith(123);

        expect(check1Spy).toHaveBeenCalledOnce();
        expect(check2Spy).toHaveBeenCalledOnce();
        expect(successSpy).toHaveBeenCalledOnce();
    });

    it('error when first consistency check fails', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const failSpy = jest.spyOn(alertService, 'error');

        comp.checkConsistencies(programmingExercise);
        expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(123);

        expect(check1Spy).toHaveBeenCalledOnce();
        expect(check2Spy).not.toHaveBeenCalled();
        expect(failSpy).toHaveBeenCalledOnce();
    });

    it('error when exercise id undefined', () => {
        const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
        const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
        const failSpy = jest.spyOn(alertService, 'error');

        comp.checkConsistencies({ id: undefined } as any);

        expect(check1Spy).not.toHaveBeenCalled();
        expect(check2Spy).not.toHaveBeenCalled();
        expect(failSpy).toHaveBeenCalledOnce();
    });

    it('check isLoading propagates correctly', () => {
        (artemisIntelligenceService as any).isLoading = () => true;
        expect(comp.isCheckingConsistency()).toBeTrue();

        (artemisIntelligenceService as any).isLoading = () => false;
        expect(comp.isCheckingConsistency()).toBeFalse();
    });

    it('returns right icon', () => {
        expect(comp.getSeverityIcon('HIGH')).toBe(faCircleExclamation);
        expect(comp.getSeverityIcon('MEDIUM')).toBe(faTriangleExclamation);
        expect(comp.getSeverityIcon('LOW')).toBe(faCircleInfo);
        expect(comp.getSeverityIcon(undefined as any)).toBe(faCircleInfo);
    });

    it('returns right color', () => {
        expect(comp.getSeverityColor('HIGH')).toBe('text-danger');
        expect(comp.getSeverityColor('MEDIUM')).toBe('text-warning');
        expect(comp.getSeverityColor('LOW')).toBe('text-info');
        expect(comp.getSeverityColor(undefined as any)).toBe('text-secondary');
    });

    it('sets selectedIssue and locationIndex correctly for new issue with deltaIndex = 1', fakeAsync(() => {
        const issue = mockIssues[4]; // multi-location issue

        comp.selectedIssue = undefined;
        comp.locationIndex = 0;

        comp.onIssueNavigate(issue, 1, new Event('click'));
        tick();

        expect(comp.selectedIssue).toBe(issue);
        expect(comp.locationIndex).toBe(0);
    }));

    it('cycles through relatedLocations and wraps around for same selected issue', fakeAsync(() => {
        const issue = mockIssues[4]; // multi-location
        (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue('TEMPLATE'); // no repo switching

        comp.selectedIssue = issue;
        comp.locationIndex = 0;

        // forward
        comp.onIssueNavigate(issue, 1, new Event('click'));
        tick();
        expect(comp.locationIndex).toBe(1);

        // forward (wrap to 0)
        comp.onIssueNavigate(issue, 1, new Event('click'));
        tick();
        expect(comp.locationIndex).toBe(2);

        // backward (wrap to last)
        comp.onIssueNavigate(issue, 1, new Event('click'));
        tick();
        expect(comp.locationIndex).toBe(0);
    }));

    it('navigates to PROBLEM_STATEMENT and calls jumpToLine without scheduling file load state', fakeAsync(() => {
        const issue = mockIssues[0]; // PROBLEM_STATEMENT
        const loc = issue.relatedLocations[0];

        const jumpToLineSpy = jest.spyOn((comp as any).editableInstructions, 'jumpToLine');

        comp.onIssueNavigate(issue, 1, new Event('click'));
        tick();

        expect((comp as any).codeEditorContainer.selectedFile).toBe((comp as any).codeEditorContainer.problemStatementIdentifier);
        expect(jumpToLineSpy).toHaveBeenCalledWith(loc.endLine);

        // early return: no jump state set
        expect(comp.lineJumpOnFileLoad).toBeUndefined();
        expect(comp.fileToJumpOn).toBeUndefined();
    }));

    it('shows error and clears jump state when repository selection fails', () => {
        const issue = mockIssues[3]; // TESTS_REPOSITORY

        (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue('SOLUTION');

        const error = new Error('repo selection failed');
        jest.spyOn(comp, 'selectTestRepository').mockImplementation(() => {
            throw error; // sync throw so try/catch is hit
        });

        const alertErrorSpy = jest.spyOn(alertService, 'error');
        const translateSpy = jest.spyOn(translateService, 'instant');
        const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

        comp.onIssueNavigate(issue, 1, new Event('click'));

        expect(translateSpy).toHaveBeenCalledWith('artemisApp.hyperion.consistencyCheck.navigationFailed');
        expect(alertErrorSpy).toHaveBeenCalled();

        expect(comp.lineJumpOnFileLoad).toBeUndefined();
        expect(comp.fileToJumpOn).toBeUndefined();

        expect(onEditorLoadedSpy).not.toHaveBeenCalled(); // <-- use the spy
    });

    it('onEditorLoaded calls onFileLoad immediately when file is already selected', () => {
        const targetFile = 'src/tests/ExampleTest.java';

        comp.fileToJumpOn = targetFile;
        (comp as any).codeEditorContainer.selectedFile = targetFile;

        const onFileLoadSpy = jest.spyOn(comp, 'onFileLoad');

        comp.onEditorLoaded();

        expect(onFileLoadSpy).toHaveBeenCalledWith(targetFile);
        // Should not re-assign selectedFile in this branch
        expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
    });

    it('onEditorLoaded sets selectedFile when file is not selected yet', () => {
        const targetFile = 'src/tests/ExampleTest.java';

        comp.fileToJumpOn = targetFile;
        (comp as any).codeEditorContainer.selectedFile = 'some/other/file.java';

        const onFileLoadSpy = jest.spyOn(comp, 'onFileLoad');

        comp.onEditorLoaded();

        // Should not call onFileLoad immediately
        expect(onFileLoadSpy).not.toHaveBeenCalled();
        // Should trigger load by selecting the file
        expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
    });

    it('onFileLoad jumps to line and clears lineJumpOnFileLoad when file matches', () => {
        const targetFile = 'src/solution/Solution.java';
        const targetLine = 60;

        comp.fileToJumpOn = targetFile;
        comp.lineJumpOnFileLoad = targetLine;

        comp.onFileLoad(targetFile);

        expect((comp as any).codeEditorContainer.jumpToLine).toHaveBeenCalledWith(targetLine);
        // lineJumpOnFileLoad should be cleared
        expect(comp.lineJumpOnFileLoad).toBeUndefined();
    });

    it('onFileLoad does nothing if file does not match fileToJumpOn', () => {
        comp.fileToJumpOn = 'src/solution/Solution.java';
        comp.lineJumpOnFileLoad = 60;

        comp.onFileLoad('src/tests/ExampleTest.java');

        expect((comp as any).codeEditorContainer.jumpToLine).not.toHaveBeenCalled();
        // should remain unchanged
        expect(comp.lineJumpOnFileLoad).toBe(60);
    });

    it('onFileLoad does nothing if lineJumpOnFileLoad is undefined', () => {
        const targetFile = 'src/solution/Solution.java';

        comp.fileToJumpOn = targetFile;
        comp.lineJumpOnFileLoad = undefined;

        comp.onFileLoad(targetFile);

        expect((comp as any).codeEditorContainer.jumpToLine).not.toHaveBeenCalled();
        expect(comp.lineJumpOnFileLoad).toBeUndefined();
    });
});
