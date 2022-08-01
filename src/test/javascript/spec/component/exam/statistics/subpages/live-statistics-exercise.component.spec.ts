import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { BehaviorSubject, of } from 'rxjs';
import { ExamLiveStatisticsService } from 'app/exam/statistics/exam-live-statistics.service';
import { ActivatedRoute } from '@angular/router';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { LiveStatisticsExercisesComponent } from 'app/exam/statistics/subpages/exercise/live-statistics-exercises.component';
import { createTestExercises } from '../exam-live-statistics-helper';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { LiveStatisticsCardComponent } from 'app/exam/statistics/subpages/live-statistics-card.component';
import { ExerciseNavigationChartComponent } from 'app/exam/statistics/charts/exercises/exercise-navigation-chart.component';
import { ExerciseChartComponent } from 'app/exam/statistics/charts/exercises/exercise-chart.component';
import { ExerciseSubmissionChartComponent } from 'app/exam/statistics/charts/exercises/exercise-submission-chart.component';
import { ExerciseGroupChartComponent } from 'app/exam/statistics/charts/exercises/exercise-group-chart.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockHttpService } from '../../../../helpers/mocks/service/mock-http.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HttpClient } from '@angular/common/http';
import { ExerciseDetailSubmissionChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-submission-chart.component';
import { ExerciseDetailNavigationChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-navigation-chart.component';
import { ExerciseDetailCurrentChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-current-chart.component';
import { ExerciseDetailTemplateChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-template-chart.component';
import { PieChartModule } from '@swimlane/ngx-charts';

describe('Live Statistics Exercise Component', () => {
    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;
    const exercises = createTestExercises(3);
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.exercises = exercises;
    exam.exerciseGroups = [exerciseGroup];

    let comp: LiveStatisticsExercisesComponent;
    let fixture: ComponentFixture<LiveStatisticsExercisesComponent>;
    let examLiveStatisticsService: ExamLiveStatisticsService;

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(PieChartModule)],
            declarations: [
                LiveStatisticsExercisesComponent,
                LiveStatisticsCardComponent,
                ExerciseNavigationChartComponent,
                ExerciseChartComponent,
                ExerciseGroupChartComponent,
                ExerciseSubmissionChartComponent,
                ExerciseDetailSubmissionChartComponent,
                ExerciseDetailNavigationChartComponent,
                ExerciseDetailCurrentChartComponent,
                ExerciseDetailTemplateChartComponent,
                DataTableComponent,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: HttpClient, useClass: MockHttpService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LiveStatisticsExercisesComponent);
                comp = fixture.componentInstance;
                examLiveStatisticsService = TestBed.inject(ExamLiveStatisticsService);
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    // On init
    it('should call getExamBehaviorSubject of examLiveStatisticsService to get the exam on init', () => {
        // GIVEN
        jest.spyOn(examLiveStatisticsService, 'getExamBehaviorSubject').mockReturnValue(new BehaviorSubject(exam));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(examLiveStatisticsService.getExamBehaviorSubject).toHaveBeenCalledOnce();
        expect(examLiveStatisticsService.getExamBehaviorSubject).toHaveBeenCalledWith(exam.id);
        expect(comp.exam).toEqual(exam);
    });

    it('should set exercise groups on init', () => {
        // GIVEN
        jest.spyOn(examLiveStatisticsService, 'getExamBehaviorSubject').mockReturnValue(new BehaviorSubject(exam));
        const expectedExercises = exam
            .exerciseGroups!.map((group) => {
                const temp = group.exercises;
                exercises?.forEach((exercise) => {
                    exercise.exerciseGroup = group;
                });
                return temp;
            })
            .filter((exercise) => !!exercise)
            .flat() as Exercise[];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.exercises).toEqual(expectedExercises);
    });

    // Prepare prop
    it('should prepare prop', () => {
        let propWithIdentifier = '_test';

        // WHEN
        propWithIdentifier = comp.prepareProp(propWithIdentifier);

        // THEN (both branches)
        expect(comp.prepareProp(propWithIdentifier)).toBe('test');
        expect(comp.prepareProp(propWithIdentifier)).toBe('test');
    });
});
