import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { QuizExerciseLifecycleButtonsComponent } from 'app/exercises/quiz/manage/quiz-exercise-lifecycle-buttons.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../../helpers/mocks/service/mock-alert.service';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import dayjs, { Dayjs } from 'dayjs/esm';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';

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

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id, examId: 12, exerciseGroupId: 23 }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseLifecycleButtonsComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        })
            .overrideTemplate(QuizExerciseDetailComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        statisticsService = fixture.debugElement.injector.get(StatisticsService);
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
