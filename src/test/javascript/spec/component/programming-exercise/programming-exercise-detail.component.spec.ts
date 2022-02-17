import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Course } from 'app/entities/course.model';
import { TranslateModule } from '@ngx-translate/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { Exam } from 'app/entities/exam.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Task } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { HttpResponse } from '@angular/common/http';

describe('ProgrammingExercise Management Detail Component', () => {
    let comp: ProgrammingExerciseDetailComponent;
    let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
    let statisticsService: StatisticsService;
    let programmingExerciseService: ProgrammingExerciseService;
    let statisticsServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    const exerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot()],
            declarations: [ProgrammingExerciseDetailComponent],
            providers: [
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useValue: new MockProfileService() },
            ],
        })
            .overrideTemplate(ProgrammingExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseDetailComponent);
        comp = fixture.componentInstance;
        statisticsService = fixture.debugElement.injector.get(StatisticsService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        statisticsServiceStub = jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
        alertService = fixture.debugElement.injector.get(AlertService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('OnInit for course exercise', () => {
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ programmingExercise });
        });

        it('Should not be in exam mode', () => {
            // WHEN
            comp.ngOnInit();

            // THEN
            expect(statisticsServiceStub).toHaveBeenCalled();
            expect(comp.programmingExercise).toEqual(programmingExercise);
            expect(comp.isExamExercise).toBeFalse();
            expect(comp.doughnutStats.participationsInPercent).toEqual(100);
            expect(comp.doughnutStats.resolvedPostsInPercent).toEqual(50);
            expect(comp.doughnutStats.absoluteAveragePoints).toEqual(5);
        });
    });

    describe('OnInit for exam exercise', () => {
        const exam = { id: 4, course: { id: 6 } as Course } as Exam;
        const exerciseGroup = { id: 9, exam };
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.id = 123;
        programmingExercise.exerciseGroup = exerciseGroup;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ programmingExercise });
        });

        it('Should be in exam mode', () => {
            // WHEN
            comp.ngOnInit();

            // THEN
            expect(statisticsServiceStub).toHaveBeenCalled();
            expect(comp.programmingExercise).toEqual(programmingExercise);
            expect(comp.isExamExercise).toBeTrue();
        });
    });

    it('should retrieve all tasks and tests extracted from the problem statement', () => {
        const tasks: Task[] = [
            {
                id: 1,
                taskName: 'Implement BubbleSort',
                tests: ['testBubbleSort', 'testBubbleSortHidden'],
                completeString: '',
                hints: [],
            },
            {
                id: 2,
                taskName: 'Implement Context',
                tests: ['testClass[Context]'],
                completeString: '',
                hints: [],
            },
        ];
        const extractTaskMock = jest.spyOn(programmingExerciseService, 'getTasksAndTestsExtractedFromProblemStatement').mockReturnValue(of(tasks));
        const expectedParams = {
            numberTasks: 2,
            numberTestCases: 3,
            detailedResult: '"Implement BubbleSort": testBubbleSort,testBubbleSortHidden\n"Implement Context": testClass[Context]',
        };
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.id = 123;
        comp.programmingExercise = programmingExercise;

        comp.getExtractedTasksAndTestsFromProblemStatement();

        expect(addAlertSpy).toHaveBeenCalledTimes(1);
        expect(addAlertSpy).toHaveBeenCalledWith({
            message: 'artemisApp.programmingExercise.extractTasksFromProblemStatementSuccess',
            timeout: 10000000,
            translationParams: expectedParams,
            type: 'success',
        });
        expect(extractTaskMock).toHaveBeenCalledTimes(1);
        expect(extractTaskMock).toHaveBeenCalledWith(programmingExercise.id);
    });

    it('should invoke deletion of tasks and solution entries', () => {
        const deleteTasksAndSolutionEntriesSpy = jest.spyOn(programmingExerciseService, 'deleteTasksWithSolutionEntries').mockReturnValue(of(new HttpResponse<void>()));
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        const programmingExercise = new ProgrammingExercise(new Course(), undefined);
        programmingExercise.id = 123;
        comp.programmingExercise = programmingExercise;

        comp.deleteTasksWithSolutionEntries();

        expect(deleteTasksAndSolutionEntriesSpy).toHaveBeenCalledTimes(1);
        expect(deleteTasksAndSolutionEntriesSpy).toHaveBeenCalledWith(programmingExercise.id);
        expect(addAlertSpy).toHaveBeenCalledTimes(1);
        expect(addAlertSpy).toHaveBeenCalledWith({
            message: 'artemisApp.programmingExercise.deleteTasksAndSolutionEntriesSuccess',
            timeout: 10000,
            type: 'success',
        });
    });
});
