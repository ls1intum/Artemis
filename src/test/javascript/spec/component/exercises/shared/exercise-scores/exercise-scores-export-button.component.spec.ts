import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Team } from 'app/entities/team.model';
import { User } from 'app/core/user/user.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockResultService } from '../../../../helpers/mocks/service/mock-result.service';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

describe('ExerciseScoresExportButtonComponent', () => {
    let component: ExerciseScoresExportButtonComponent;
    let fixture: ComponentFixture<ExerciseScoresExportButtonComponent>;
    let resultService: ResultService;

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

    const participation1 = setupParticipation('studentA', 'Student A', true);
    const participation2 = setupParticipation('studentB', 'Student B', true);

    const result1 = new Result();
    result1.id = 1;
    result1.score = 1;
    result1.participation = participation1;

    const resultWithPoints1 = new ResultWithPointsPerGradingCriterion();
    resultWithPoints1.result = result1;
    resultWithPoints1.totalPoints = 2;
    resultWithPoints1.pointsPerCriterion = new Map([
        [1, 2.0],
        [3, 10.0],
    ]);

    const result2 = new Result();
    result2.id = 2;
    result2.score = 2;
    result2.participation = participation2;

    const resultWithPoints2 = new ResultWithPointsPerGradingCriterion();
    resultWithPoints2.result = result2;
    resultWithPoints2.totalPoints = 4;
    resultWithPoints2.pointsPerCriterion = new Map();

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
            'Repo Link': 'https://www.gitlab.local/studentA',
        },
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Criterion 1': 0,
            'Unnamed Criterion 1': 0,
            'Unnamed Criterion 2': 0,
            'Repo Link': 'https://www.gitlab.local/studentB',
        },
    ];

    const expectedColumnsNoCriteria = ['Name', 'Username', 'Score', 'Points', 'Repo Link'];
    const expectedRowsNoCriteria = [
        {
            Name: 'Student A',
            Username: 'studentA',
            Score: 1,
            Points: 2,
            'Repo Link': 'https://www.gitlab.local/studentA',
        },
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Repo Link': 'https://www.gitlab.local/studentB',
        },
    ];

    const expectedColumnsWithTestCases = ['Name', 'Username', 'Score', 'Points', 'Repo Link', 'TestName1', 'TestName2', 'Test 3'];
    const expectedRowsWithTestCases = [
        {
            Name: 'Student B',
            Username: 'studentB',
            Score: 2,
            Points: 4,
            'Repo Link': 'https://www.gitlab.local/studentB',
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
            'Repo Link': 'https://www.gitlab.local/studentB',
            TestName1: 'Passed',
            TestName2: 'Failed: "Detailed text with \nnewlines and \nsymbols ;.\'~""',
            'Test 3': 'Failed',
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip)],
            declarations: [ExerciseScoresExportButtonComponent, MockComponent(FaIconComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(AlertService), { provide: ResultService, useClass: MockResultService }, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseScoresExportButtonComponent);
        component = fixture.componentInstance;
        resultService = TestBed.inject(ResultService);
    });

    beforeEach(() => {
        jest.restoreAllMocks();
    });

    it('should not start the export if the exercise has no results', () => {
        // GIVEN
        const exportCSVStub = jest.spyOn(resultService, 'triggerDownloadCSV');
        const getResultsStub = jest.spyOn(resultService, 'getResultsWithPointsPerGradingCriterion').mockReturnValue(of(new HttpResponse({ body: [] })));
        component.exercise = exercise1;

        // WHEN
        component.exportResults(false, false);
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).toHaveBeenCalledOnce();
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

        const team = new Team();
        team.name = 'Testteam 01';
        team.shortName = 'tt01';
        team.students = createStudentsForTeam(4);

        const teamParticipation = setupParticipation(team.shortName, team.name, false);
        teamParticipation.team = team;

        const teamResult = new Result();
        teamResult.id = 3;
        teamResult.score = 50;
        teamResult.participation = teamParticipation;

        const teamResultWithPoints = new ResultWithPointsPerGradingCriterion();
        teamResultWithPoints.result = teamResult;
        teamResultWithPoints.totalPoints = 100;
        teamResultWithPoints.pointsPerCriterion = new Map();

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
        const exportAsCsvStub = jest.spyOn(ExerciseScoresExportButtonComponent, 'exportAsCsv').mockImplementation();
        const getResultsStub = jest
            .spyOn(resultService, 'getResultsWithPointsPerGradingCriterion')
            .mockReturnValue(of(new HttpResponse({ body: [resultWithPoints1, resultWithPoints2] })));
        component.exercises = [exercise1, exercise2];

        // WHEN
        component.exportResults(false, false);
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).toHaveBeenCalledTimes(2);
        expect(exportAsCsvStub).toHaveBeenCalledTimes(2);
        expect(exportAsCsvStub).toHaveBeenNthCalledWith(1, 'ex1-results-scores', expectedColumnsWithCriteria, expectedRowsWithCriteria);
        expect(exportAsCsvStub).toHaveBeenNthCalledWith(2, 'Exercise_title_with_spaces-results-scores', expectedColumnsNoCriteria, expectedRowsNoCriteria);
    });

    it('should export results with test cases', () => {
        const resultWithPointsAndTestCases = Object.assign({}, resultWithPoints2);

        resultWithPointsAndTestCases.result.feedbacks = createFeedbacks();
        testCsvExport(exercise2, [resultWithPointsAndTestCases], 'Exercise_title_with_spaces-results-scores', expectedColumnsWithTestCases, expectedRowsWithTestCases, true);
    });

    it('should export results with test cases and feedback', () => {
        const resultWithPointsAndTestCases = Object.assign({}, resultWithPoints2);

        resultWithPointsAndTestCases.result.feedbacks = createFeedbacks();
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
        const exportAsCsvStub = jest.spyOn(ExerciseScoresExportButtonComponent, 'exportAsCsv').mockImplementation();
        const getResultsStub = jest.spyOn(resultService, 'getResultsWithPointsPerGradingCriterion').mockReturnValue(of(new HttpResponse({ body: results })));
        component.exercise = exercise;

        // WHEN
        component.exportResults(withTestCases, withFeedback);
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).toHaveBeenCalledOnce();
        expect(exportAsCsvStub).toHaveBeenCalledOnce();
        if (withFeedback) {
            expect(exportAsCsvStub).toHaveBeenCalledWith(expectedCsvFilename, expectedCsvColumns, expectedCsvRows, ',');
        } else {
            expect(exportAsCsvStub).toHaveBeenCalledWith(expectedCsvFilename, expectedCsvColumns, expectedCsvRows);
        }
    }

    function setupParticipation(studentLogin: string, studentName: string, isProgramming: boolean): StudentParticipation {
        const participation = new StudentParticipation();
        participation.results = [];
        participation.participantIdentifier = studentLogin;
        participation.participantName = studentName;
        if (isProgramming) {
            (participation as ProgrammingExerciseStudentParticipation).repositoryUri = `https://www.gitlab.local/${studentLogin}`;
        }

        return participation;
    }

    function createStudentsForTeam(count: number): User[] {
        const users: User[] = [];
        for (let i = 1; i <= count; ++i) {
            const user = new User();
            user.name = `Student ${i}`;
            user.login = `student${i}`;
            users.push(user);
        }
        return users;
    }

    function createFeedbacks(): Feedback[] {
        const feedbacks: Feedback[] = [];

        const feedback1 = new Feedback();
        feedback1.testCase = { testName: 'TestName1' };
        feedback1.positive = true;
        feedback1.type = FeedbackType.AUTOMATIC;

        const feedback2 = new Feedback();
        feedback2.testCase = { testName: 'TestName2' };
        feedback2.positive = false;
        feedback2.detailText = 'Detailed text with \nnewlines and \nsymbols ;.\'~"';
        feedback2.type = FeedbackType.AUTOMATIC;

        const feedback3 = new Feedback();
        feedback3.testCase = {}; // no test case name -> fallback "Test 3"
        feedback3.positive = false;
        feedback3.type = FeedbackType.AUTOMATIC;
        // no detail text -> generic 'Failed' fallback

        const feedback4 = new Feedback();
        feedback4.text = 'File src/test/exercise/Main.java at line 123';
        feedback4.positive = false;
        feedback4.detailText = 'This is a manual feedback';
        feedback4.type = FeedbackType.MANUAL;

        feedbacks.push(feedback1, feedback2, feedback3, feedback4);

        return feedbacks;
    }
});
