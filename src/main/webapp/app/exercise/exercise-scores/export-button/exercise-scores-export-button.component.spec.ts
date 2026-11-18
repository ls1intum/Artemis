import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseScoresExportButtonComponent } from 'app/exercise/exercise-scores/export-button/exercise-scores-export-button.component';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { ResultService } from 'app/exercise/result/result.service';
import type { ResultWithPointsPerGradingCriterion } from 'app/openapi/model/result-with-points-per-grading-criterion';
import type { ParticipationForExport } from 'app/openapi/model/participation-for-export';
import type { Feedback as FeedbackResponse } from 'app/openapi/model/feedback';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { AlertService } from 'app/foundation/service/alert.service';

describe('ExerciseScoresExportButtonComponent', () => {
    let component: ExerciseScoresExportButtonComponent;
    let fixture: ComponentFixture<ExerciseScoresExportButtonComponent>;
    let resultService: ResultService;
    let httpMock: HttpTestingController;

    const course1 = new Course();
    course1.id = 1;

    const exerciseGroup1 = new ExerciseGroup();
    exerciseGroup1.id = 1;

    const gradingCriterion1 = new GradingCriterion();
    gradingCriterion1.id = 1;
    gradingCriterion1.title = 'Criterion 1';

    const gradingCriterion2 = new GradingCriterion();
    gradingCriterion2.id = 2;

    const gradingCriterion3 = new GradingCriterion();
    gradingCriterion3.id = 3;

    const exercise1 = new ProgrammingExercise(course1, exerciseGroup1);
    exercise1.id = 1;
    exercise1.shortName = 'ex1';
    exercise1.maxPoints = 200;
    exercise1.gradingCriteria = [gradingCriterion1, gradingCriterion2, gradingCriterion3];

    const exercise2 = new ProgrammingExercise(course1, exerciseGroup1);
    exercise2.id = 2;
    exercise2.title = 'Exercise  title with spaces';
    exercise2.shortName = undefined;
    exercise2.maxPoints = 200;

    const resultWithPoints1: ResultWithPointsPerGradingCriterion = {
        result: { id: 1, score: 1, submission: { id: 1, participation: setupParticipation('studentA', 'Student A', true) } },
        totalPoints: 2,
        pointsPerCriterion: { '1': 2.0, '3': 10.0 },
    };

    const resultWithPoints2: ResultWithPointsPerGradingCriterion = {
        result: { id: 2, score: 2, submission: { id: 2, participation: setupParticipation('studentB', 'Student B', true) } },
        totalPoints: 4,
        pointsPerCriterion: {},
    };

    const expectedColumnsWithCriteria = ['Name', 'Username', 'Score', 'Points', 'Criterion 1', 'Unnamed Criterion 1', 'Unnamed Criterion 2', 'Repo Link'];
    const expectedRowsWithCriteria = [
        {
            Name: 'Student A',
            Username: 'studentA',
            Score: 1,
            Points: 2,
            'Criterion 1': 2,
            'Unnamed Criterion 1': 0,
            'Unnamed Criterion 2': 10,
            'Repo Link': 'https://www.artemis.local/studentA',
        },
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Criterion 1': 0,
            'Unnamed Criterion 1': 0,
            'Unnamed Criterion 2': 0,
            'Repo Link': 'https://www.artemis.local/studentB',
        },
    ];

    const expectedColumnsNoCriteria = ['Name', 'Username', 'Score', 'Points', 'Repo Link'];
    const expectedRowsNoCriteria = [
        {
            Name: 'Student A',
            Username: 'studentA',
            Score: 1,
            Points: 2,
            'Repo Link': 'https://www.artemis.local/studentA',
        },
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Repo Link': 'https://www.artemis.local/studentB',
        },
    ];

    const expectedColumnsWithTestCases = ['Name', 'Username', 'Score', 'Points', 'Repo Link', 'TestName1', 'TestName2', 'Test 3'];
    const expectedRowsWithTestCases = [
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Repo Link': 'https://www.artemis.local/studentB',
            TestName1: 'Passed',
            TestName2: 'Failed',
            'Test 3': 'Failed',
        },
    ];

    const expectedRowsWithTestCasesAndFeedback = [
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Repo Link': 'https://www.artemis.local/studentB',
            TestName1: 'Passed',
            TestName2: 'Failed: "Detailed text with \nnewlines and \nsymbols ;.\'~""',
            'Test 3': 'Failed',
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExerciseScoresExportButtonComponent],
            providers: [
                MockProvider(AlertService),
                { provide: ResultService, useClass: MockResultService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseScoresExportButtonComponent);
        component = fixture.componentInstance;
        resultService = TestBed.inject(ResultService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    const flushResults = (exerciseId: number, body: ResultWithPointsPerGradingCriterion[]) => {
        const request = httpMock.expectOne({ method: 'GET', url: `/api/assessment/exercises/${exerciseId}/results-with-points-per-criterion?withSubmissions=false` });
        request.flush(body);
        return request;
    };

    it.each([
        [0, false, false],
        [1, true, false],
        [2, true, true],
    ] as const)('selects result export option %s from the nested dropdown', async (index, withTestCases, withFeedback) => {
        fixture.componentRef.setInput('exercise', exercise1);
        fixture.componentRef.setInput('inDropdown', true);
        const exportResults = vi.spyOn(component, 'exportResults').mockImplementation(() => {});
        fixture.detectChanges();
        const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('#export-results-dropdown');
        trigger.click();
        fixture.detectChanges();
        await fixture.whenStable();

        const options = document.querySelectorAll<HTMLButtonElement>('[role="menuitem"]');
        expect(options).toHaveLength(3);
        expect(trigger.getAttribute('aria-expanded')).toBe('true');
        options[index].click();
        fixture.detectChanges();
        expect(exportResults).toHaveBeenCalledExactlyOnceWith(withTestCases, withFeedback);
        expect(document.querySelector('[role="menu"]')).toBeNull();
    });

    it('should not start the export if the exercise has no results', () => {
        // GIVEN
        const exportCSVStub = vi.spyOn(resultService, 'triggerDownloadCSV');
        fixture.componentRef.setInput('exercise', exercise1);

        // WHEN
        component.exportResults(false, false);
        flushResults(1, []);
        fixture.changeDetectorRef.detectChanges();

        // THEN
        expect(exportCSVStub).not.toHaveBeenCalled();
    });

    it('should export results for one exercise', () => {
        testCsvExport(exercise1, [resultWithPoints1, resultWithPoints2], 'ex1-results-scores', expectedColumnsWithCriteria, expectedRowsWithCriteria);
    });

    it('should export results for a team exercise', () => {
        const exerciseTeam = new FileUploadExercise(course1, exerciseGroup1);
        exerciseTeam.id = 1;
        exerciseTeam.shortName = 'ex1';
        exerciseTeam.maxPoints = 200;

        const teamParticipation: ParticipationForExport = {
            id: 7,
            participantName: 'Testteam 01',
            participantIdentifier: 'tt01',
            team: { id: 1, name: 'Testteam 01', shortName: 'tt01', students: createStudentsForTeam(4) },
        };
        const teamResultWithPoints: ResultWithPointsPerGradingCriterion = {
            result: { id: 3, score: 50, submission: { id: 3, participation: teamParticipation } },
            totalPoints: 100,
            pointsPerCriterion: {},
        };

        const expectedTeamColumns = ['Team Name', 'Team Short Name', 'Score', 'Points', 'Students'];
        const expectedRow = {
            'Team Name': 'Testteam 01',
            'Team Short Name': 'tt01',
            Points: 100,
            Score: 50,
            Students: 'Student 1, Student 2, Student 3, Student 4',
        };

        testCsvExport(exerciseTeam, [teamResultWithPoints], 'ex1-results-scores', expectedTeamColumns, [expectedRow]);
    });

    it('should export results for multiple exercises', () => {
        // GIVEN
        // @ts-ignore (stubbing a private method)
        const exportAsCsvStub = vi.spyOn(ExerciseScoresExportButtonComponent, 'exportAsCsv').mockImplementation();
        fixture.componentRef.setInput('exercises', [exercise1, exercise2]);

        // WHEN
        component.exportResults(false, false);
        flushResults(1, [resultWithPoints1, resultWithPoints2]);
        flushResults(2, [resultWithPoints1, resultWithPoints2]);
        fixture.changeDetectorRef.detectChanges();

        // THEN
        expect(exportAsCsvStub).toHaveBeenCalledTimes(2);
        expect(exportAsCsvStub).toHaveBeenNthCalledWith(1, 'ex1-results-scores', expectedColumnsWithCriteria, expectedRowsWithCriteria);
        expect(exportAsCsvStub).toHaveBeenNthCalledWith(2, 'Exercise_title_with_spaces-results-scores', expectedColumnsNoCriteria, expectedRowsNoCriteria);
    });

    it('should export results with test cases', () => {
        const resultWithPointsAndTestCases = withFeedbacks(resultWithPoints2);

        testCsvExport(exercise2, [resultWithPointsAndTestCases], 'Exercise_title_with_spaces-results-scores', expectedColumnsWithTestCases, expectedRowsWithTestCases, true);
    });

    it('should export results with test cases and feedback', () => {
        const resultWithPointsAndTestCases = withFeedbacks(resultWithPoints2);

        testCsvExport(
            exercise2,
            [resultWithPointsAndTestCases],
            'Exercise_title_with_spaces-results-scores',
            expectedColumnsWithTestCases,
            expectedRowsWithTestCasesAndFeedback,
            true,
            true,
        );
    });

    function testCsvExport(
        exercise: Exercise,
        results: ResultWithPointsPerGradingCriterion[],
        expectedCsvFilename: string,
        expectedCsvColumns: string[],
        expectedCsvRows: any[],
        withTestCases = false,
        withFeedback = false,
    ) {
        // GIVEN
        // @ts-ignore (stubbing a private method)
        const exportAsCsvStub = vi.spyOn(ExerciseScoresExportButtonComponent, 'exportAsCsv').mockImplementation();
        fixture.componentRef.setInput('exercise', exercise);

        // WHEN
        component.exportResults(withTestCases, withFeedback);
        flushResults(exercise.id!, results);
        fixture.changeDetectorRef.detectChanges();

        // THEN
        expect(exportAsCsvStub).toHaveBeenCalledOnce();
        if (withFeedback) {
            expect(exportAsCsvStub).toHaveBeenCalledWith(expectedCsvFilename, expectedCsvColumns, expectedCsvRows, ',');
        } else {
            expect(exportAsCsvStub).toHaveBeenCalledWith(expectedCsvFilename, expectedCsvColumns, expectedCsvRows);
        }
    }

    function setupParticipation(studentLogin: string, studentName: string, isProgramming: boolean): ParticipationForExport {
        return {
            participantIdentifier: studentLogin,
            participantName: studentName,
            repositoryUri: isProgramming ? `https://www.artemis.local/${studentLogin}` : undefined,
        };
    }

    function createStudentsForTeam(count: number) {
        return Array.from({ length: count }, (_, index) => ({ id: index + 1, login: `student${index + 1}`, name: `Student ${index + 1}` }));
    }

    function withFeedbacks(resultWithPoints: ResultWithPointsPerGradingCriterion): ResultWithPointsPerGradingCriterion {
        return { ...resultWithPoints, result: { ...resultWithPoints.result, feedbacks: createFeedbacks() } };
    }

    function createFeedbacks(): FeedbackResponse[] {
        return [
            { testCase: { testName: 'TestName1' }, positive: true, type: 'AUTOMATIC' },
            { testCase: { testName: 'TestName2' }, positive: false, detailText: 'Detailed text with \nnewlines and \nsymbols ;.\'~"', type: 'AUTOMATIC' },
            // no test case name -> fallback "Test 3", no detail text -> generic 'Failed' fallback
            { testCase: {}, positive: false, type: 'AUTOMATIC' },
            { text: 'File src/test/exercise/Main.java at line 123', positive: false, detailText: 'This is a manual feedback', type: 'MANUAL' },
        ];
    }
});
