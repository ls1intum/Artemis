/**
 * Tests for TextExerciseDetailComponent.
 * Verifies the component's behavior when displaying text exercise details for both course and exam exercises.
 */
import { Component, input } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { TextExerciseDetailComponent } from 'app/text/manage/detail/text-exercise-detail.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DetailOverviewListComponent, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencyExerciseLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';

// Mock child components to avoid their complex dependencies
@Component({
    selector: 'jhi-non-programming-exercise-detail-common-actions',
    template: '',
    standalone: true,
})
class MockNonProgrammingExerciseDetailCommonActionsComponent {
    isExamExercise = input<boolean>();
    course = input<any>();
    exercise = input<any>();
}

@Component({
    selector: 'jhi-exercise-detail-statistics',
    template: '',
    standalone: true,
})
class MockExerciseDetailStatisticsComponent {
    exercise = input<any>();
    doughnutStats = input<any>();
    exerciseType = input<any>();
}

@Component({
    selector: 'jhi-detail-overview-list',
    template: '',
    standalone: true,
})
class MockDetailOverviewListComponent {
    sections = input<any>();
}

@Component({
    selector: 'jhi-documentation-button',
    template: '',
    standalone: true,
})
class MockDocumentationButtonComponent {
    type = input<any>();
}

describe('TextExercise Management Detail Component', () => {
    setupTestBed({ zoneless: true });
    let comp: TextExerciseDetailComponent;
    let fixture: ComponentFixture<TextExerciseDetailComponent>;
    let exerciseService: TextExerciseService;
    let statisticsService: StatisticsService;

    const textExerciseStatistics = {
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextExerciseDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideComponent(TextExerciseDetailComponent, {
                remove: {
                    imports: [
                        NonProgrammingExerciseDetailCommonActionsComponent,
                        ExerciseDetailStatisticsComponent,
                        DetailOverviewListComponent,
                        DocumentationButtonComponent,
                        TranslateDirective,
                    ],
                },
                add: {
                    imports: [
                        MockNonProgrammingExerciseDetailCommonActionsComponent,
                        MockExerciseDetailStatisticsComponent,
                        MockDetailOverviewListComponent,
                        MockDocumentationButtonComponent,
                        MockDirective(TranslateDirective),
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(TextExerciseDetailComponent);
        comp = fixture.componentInstance;
        exerciseService = TestBed.inject(TextExerciseService);
        statisticsService = TestBed.inject(StatisticsService);
    });

    describe('onInit with course exercise', () => {
        const course: Course = { id: 123 } as Course;
        const textExerciseWithCourse: TextExercise = new TextExercise(course, undefined);
        textExerciseWithCourse.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithCourse.id });
        });

        it('should call load on init and be not in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            const exerciseServiceStub = vi.spyOn(exerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: textExerciseWithCourse,
                        headers,
                    }),
                ),
            );
            const statisticsServiceStub = vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(textExerciseStatistics));

            // WHEN
            fixture.detectChanges();

            // THEN
            expect(exerciseServiceStub).toHaveBeenCalledOnce();
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            expect(comp.isExamExercise).toBe(false);
            expect(comp.textExercise).toEqual(textExerciseWithCourse);
            expect(comp.doughnutStats.participationsInPercent).toBe(100);
            expect(comp.doughnutStats.resolvedPostsInPercent).toBe(50);
            expect(comp.doughnutStats.absoluteAveragePoints).toBe(5);
        });
    });

    describe('onInit with exam exercise', () => {
        const exerciseGroup: ExerciseGroup = new ExerciseGroup();
        const textExerciseWithExerciseGroup: TextExercise = new TextExercise(undefined, exerciseGroup);
        textExerciseWithExerciseGroup.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithExerciseGroup.id });
        });

        it('should call load on init and be in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            const exerciseServiceStub = vi.spyOn(exerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: textExerciseWithExerciseGroup,
                        headers,
                    }),
                ),
            );
            const statisticsServiceStub = vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(textExerciseStatistics));

            // WHEN
            fixture.detectChanges();

            // THEN
            expect(exerciseServiceStub).toHaveBeenCalledOnce();
            expect(statisticsServiceStub).toHaveBeenCalledOnce();
            expect(comp.isExamExercise).toBe(true);
            expect(comp.textExercise).toEqual(textExerciseWithExerciseGroup);
        });
    });

    describe('competency links display', () => {
        const course: Course = { id: 123 } as Course;
        const textExerciseWithCompetencies: TextExercise = new TextExercise(course, undefined);
        textExerciseWithCompetencies.id = 123;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ exerciseId: textExerciseWithCompetencies.id });
        });

        it('should display competency links when exercise has competencies', () => {
            const competency1 = { id: 1, title: 'Competency 1' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Competency 2' } as CourseCompetency;
            textExerciseWithCompetencies.competencyLinks = [{ competency: competency1 } as CompetencyExerciseLink, { competency: competency2 } as CompetencyExerciseLink];

            const headers = new HttpHeaders().append('link', 'link;link');
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExerciseWithCompetencies, headers })));
            vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(textExerciseStatistics));

            fixture.detectChanges();

            expect(comp.detailOverviewSections).toBeDefined();
            const problemSection = comp.detailOverviewSections.find((section) => section.headline === 'artemisApp.exercise.sections.problem');
            expect(problemSection).toBeDefined();
            const competencyDetail = problemSection?.details.find((detail) => detail && 'title' in detail && detail.title === 'artemisApp.competency.link.title');
            expect(competencyDetail).toBeDefined();
            expect(competencyDetail).toHaveProperty('type', DetailType.Text);
            expect(competencyDetail).toHaveProperty('data.text', 'Competency 1, Competency 2');
        });

        it('should not display competency links when exercise has no competencies', () => {
            const exerciseWithoutCompetencies = new TextExercise(course, undefined);
            exerciseWithoutCompetencies.id = 123;
            exerciseWithoutCompetencies.competencyLinks = [];

            const headers = new HttpHeaders().append('link', 'link;link');
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseWithoutCompetencies, headers })));
            vi.spyOn(statisticsService, 'getExerciseStatistics').mockReturnValue(of(textExerciseStatistics));

            fixture.detectChanges();

            expect(comp.detailOverviewSections).toBeDefined();
            const problemSection = comp.detailOverviewSections.find((section) => section.headline === 'artemisApp.exercise.sections.problem');
            expect(problemSection).toBeDefined();
            const competencyDetail = problemSection?.details.find((detail) => detail && 'title' in detail && detail.title === 'artemisApp.competency.link.title');
            expect(competencyDetail).toBeUndefined();
        });
    });
});
