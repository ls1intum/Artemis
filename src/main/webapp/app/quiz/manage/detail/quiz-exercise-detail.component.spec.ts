import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { QuizExerciseDetailComponent } from 'app/quiz/manage/detail/quiz-exercise-detail.component';
import dayjs, { Dayjs } from 'dayjs/esm';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('QuizExercise Details Component', () => {
    let comp: QuizExerciseDetailComponent;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;
    let quizExerciseService: QuizExerciseService;
    let statisticsService: StatisticsService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    quizExercise.quizQuestions = [];
    quizExercise.isAtLeastEditor = true;
    quizExercise.dueDate = dayjs() as Dayjs;
    quizExercise.isEditable = undefined;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id, examId: 12, exerciseGroupId: 23 }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(QuizExerciseDetailComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        quizExerciseService = TestBed.inject(QuizExerciseService);
        statisticsService = TestBed.inject(StatisticsService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be in exam mode', () => {
        jest.spyOn(comp, 'load').mockReturnValue();
        comp.ngOnInit();

        expect(comp.isExamMode).toBeTrue();
    });

    it('should initialize detail component', async () => {
        jest.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
        jest.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
        jest.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of({} as unknown as ExerciseManagementStatisticsDto));

        comp.ngOnInit();

        expect(comp.quizExercise).toBeDefined();
        expect(comp.quizExercise.isEditable).toBeTruthy();
        expect(comp.quizExercise.status).toBe(QuizStatus.VISIBLE);
        expect(comp.quizExercise.startDate).toBeDefined();

        await Promise.resolve();

        expect(comp.detailOverviewSections).toBeDefined();
    });
});
