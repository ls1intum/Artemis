import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { CompetencyExerciseLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';

describe('QuizExercise Details Component', () => {
    setupTestBed({ zoneless: true });

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
        vi.restoreAllMocks();
    });

    it('should be in exam mode', () => {
        vi.spyOn(comp, 'load').mockReturnValue();
        comp.ngOnInit();

        expect(comp.isExamMode).toBe(true);
    });

    it('should initialize detail component', async () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
        vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
        vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of({} as unknown as ExerciseManagementStatisticsDto));

        comp.ngOnInit();

        expect(comp.quizExercise).toBeDefined();
        expect(comp.quizExercise.isEditable).toBeTruthy();
        expect(comp.quizExercise.status).toBe(QuizStatus.VISIBLE);
        expect(comp.quizExercise.startDate).toBeDefined();

        await Promise.resolve();

        expect(comp.detailOverviewSections).toBeDefined();
    });

    it('should display competency links when exercise has competencies', async () => {
        const competency1 = { id: 1, title: 'Competency 1' } as CourseCompetency;
        const competency2 = { id: 2, title: 'Competency 2' } as CourseCompetency;
        const exerciseWithCompetencies = { ...quizExercise };
        exerciseWithCompetencies.competencyLinks = [{ competency: competency1 } as CompetencyExerciseLink, { competency: competency2 } as CompetencyExerciseLink];

        vi.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseWithCompetencies })));
        vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
        vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of({} as unknown as ExerciseManagementStatisticsDto));

        comp.ngOnInit();
        await Promise.resolve();

        expect(comp.detailOverviewSections).toBeDefined();
        const modeSection = comp.detailOverviewSections.find((section) => section.headline === 'artemisApp.exercise.sections.mode');
        expect(modeSection).toBeDefined();
        const competencyDetail = modeSection?.details.find((detail) => detail && 'title' in detail && detail.title === 'artemisApp.competency.link.title');
        expect(competencyDetail).toBeDefined();
        expect(competencyDetail).toHaveProperty('type', DetailType.Text);
        expect(competencyDetail).toHaveProperty('data.text', 'Competency 1, Competency 2');
    });

    it('should not display competency links when exercise has no competencies', async () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: quizExercise })));
        vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
        vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of({} as unknown as ExerciseManagementStatisticsDto));

        comp.ngOnInit();
        await Promise.resolve();

        expect(comp.detailOverviewSections).toBeDefined();
        const modeSection = comp.detailOverviewSections.find((section) => section.headline === 'artemisApp.exercise.sections.mode');
        expect(modeSection).toBeDefined();
        const competencyDetail = modeSection?.details.find((detail) => detail && 'title' in detail && detail.title === 'artemisApp.competency.link.title');
        expect(competencyDetail).toBeUndefined();
    });
});
