import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Team } from 'app/entities/team.model';
import { User } from 'app/core/user/user.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { AlertService } from 'app/core/util/alert.service';

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
    gradingCriterion2.title = 'Criterion 2';

    const exercise1 = new ProgrammingExercise(course1, exerciseGroup1);
    exercise1.id = 1;
    exercise1.shortName = 'ex1';
    exercise1.maxPoints = 200;
    exercise1.gradingCriteria = [gradingCriterion1, gradingCriterion2];

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
        [2, 10.0],
    ]);

    const result2 = new Result();
    result2.id = 2;
    result2.score = 2;
    result2.participation = participation2;

    const resultWithPoints2 = new ResultWithPointsPerGradingCriterion();
    resultWithPoints2.result = result2;
    resultWithPoints2.totalPoints = 4;
    resultWithPoints2.pointsPerCriterion = new Map();

    const expectedCSVWithCriteria = [
        'data:text/csv;charset=utf-8,Name,Username,Score,Points,"Criterion 1","Criterion 2",Repo Link',
        '"Student A",studentA,1,2,2,10,https://www.gitlab.local/studentA',
        '"Student B",studentB,2,4,0,0,https://www.gitlab.local/studentB',
    ];
    const expectedCSVNoCriteria = [
        'data:text/csv;charset=utf-8,Name,Username,Score,Points,Repo Link',
        '"Student A",studentA,1,2,https://www.gitlab.local/studentA',
        '"Student B",studentB,2,4,https://www.gitlab.local/studentB',
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ExerciseScoresExportButtonComponent, MockComponent(FaIconComponent), MockDirective(TranslateDirective)],
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
        component.exportResults();
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).toHaveBeenCalled();
        expect(exportCSVStub).not.toHaveBeenCalled();
    });

    it('should export results for one exercise', () => {
        testCsvExport(exercise1, [resultWithPoints1, resultWithPoints2], expectedCSVWithCriteria, 'ex1-results-scores.csv');
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

        const expectedCSVTeamExercise = [
            'data:text/csv;charset=utf-8,Team Name,Team Short Name,Score,Points,Students',
            '"Testteam 01",tt01,50,100,"Student 1, Student 2, Student 3, Student 4"',
        ];

        testCsvExport(exerciseTeam, [teamResultWithPoints], expectedCSVTeamExercise, 'ex1-results-scores.csv');
    });

    it('should export results for multiple exercise', () => {
        // GIVEN
        const exportCSVStub = jest.spyOn(resultService, 'triggerDownloadCSV');
        const getResultsStub = jest
            .spyOn(resultService, 'getResultsWithPointsPerGradingCriterion')
            .mockReturnValue(of(new HttpResponse({ body: [resultWithPoints1, resultWithPoints2] })));
        component.exercises = [exercise1, exercise2];

        // WHEN
        component.exportResults();
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).toHaveBeenCalledTimes(2);
        expect(exportCSVStub).toHaveBeenCalledTimes(2);
        expect(exportCSVStub).toHaveBeenNthCalledWith(1, expectedCSVWithCriteria, 'ex1-results-scores.csv');
        expect(exportCSVStub).toHaveBeenNthCalledWith(2, expectedCSVNoCriteria, 'Exercise_title_with_spaces-results-scores.csv');
    });

    function testCsvExport(exercise: Exercise, results: ResultWithPointsPerGradingCriterion[], expectedCsvRows: string[], expectedCsvFilename: string) {
        // GIVEN
        const exportCSVStub = jest.spyOn(resultService, 'triggerDownloadCSV');
        const getResultsStub = jest.spyOn(resultService, 'getResultsWithPointsPerGradingCriterion').mockReturnValue(of(new HttpResponse({ body: results })));
        component.exercise = exercise;

        // WHEN
        component.exportResults();
        fixture.detectChanges();

        // THEN
        expect(getResultsStub).toHaveBeenCalled();
        expect(exportCSVStub).toHaveBeenCalledWith(expectedCsvRows, expectedCsvFilename);
    }

    function setupParticipation(studentLogin: string, studentName: string, isProgramming: boolean): StudentParticipation {
        const participation = new StudentParticipation();
        participation.results = [];
        participation.participantIdentifier = studentLogin;
        participation.participantName = studentName;
        if (isProgramming) {
            (participation as ProgrammingExerciseStudentParticipation).repositoryUrl = `https://www.gitlab.local/${studentLogin}`;
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
});
