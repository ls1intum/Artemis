import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { MockComponent, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { Authority } from 'app/foundation/constants/authority.constants';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { EntityTitleService } from 'app/core/navbar/entity-title.service';
import { StatisticsSpanSelectorComponent } from 'app/exercise/statistics/statistics-span-selector/statistics-span-selector.component';
import { ExerciseDetailStatisticsComponent } from './exercise-detail-statistic/exercise-detail-statistics.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/exercise/statistics-graph/score-distribution-graph/statistics-score-distribution-graph.component';
import { StatisticsGraphComponent } from 'app/exercise/statistics-graph/statistics-graph.component';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { ExerciseStatisticsComponent } from 'app/exercise/statistics/exercise-statistics.component';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('ExerciseStatisticsComponent', () => {
    let fixture: ComponentFixture<ExerciseStatisticsComponent>;
    let component: ExerciseStatisticsComponent;
    let service: StatisticsService;
    let exerciseService: ExerciseService;

    let statisticsSpy: ReturnType<typeof vi.spyOn>;
    let exerciseSpy: ReturnType<typeof vi.spyOn>;

    const exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        course: {
            id: 2,
        },
    } as Exercise;

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseStatisticsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: '2', exerciseId: '1' }) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ExerciseStatisticsComponent, '<button id="option3" (click)="onTabChanged(SpanType.MONTH)"></button>')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseStatisticsComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(StatisticsService);
        exerciseService = TestBed.inject(ExerciseService);
        statisticsSpy = vi.spyOn(service, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
        exerciseSpy = vi.spyOn(exerciseService, 'findForStaff').mockReturnValue(of({ body: exercise } as HttpResponse<Exercise>));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).not.toBeNull();
        expect(statisticsSpy).toHaveBeenCalledTimes(1);
        expect(exerciseSpy).toHaveBeenCalledTimes(1);
        expect(component.exerciseStatistics()?.participationsInPercent).toBe(100);
        expect(component.exerciseStatistics()?.resolvedPostsInPercent).toBe(50);
        expect(component.exerciseStatistics()?.absoluteAveragePoints).toBe(5);
    });

    it('should trigger when tab changed', () => {
        const tabSpy = vi.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        expect(tabSpy).toHaveBeenCalledTimes(1);
        expect(component.currentSpan()).toBe(SpanType.MONTH);
        expect(statisticsSpy).toHaveBeenCalledTimes(1);
        expect(exerciseSpy).toHaveBeenCalledTimes(1);
        expect(component.exerciseStatistics()?.participationsInPercent).toBe(100);
        expect(component.exerciseStatistics()?.resolvedPostsInPercent).toBe(50);
        expect(component.exerciseStatistics()?.absoluteAveragePoints).toBe(5);
    });
});

describe('ExerciseStatisticsComponent explicit course capability', () => {
    let http: HttpTestingController;

    afterEach(() => {
        http.verify();
    });

    it.each([
        { label: 'post-exam tutor', role: 'TEACHING_ASSISTANT', exam: true, foreignEditor: false, futureEnd: false, suffix: 'for-exam-assessment' },
        { label: 'tutor who edits another course', role: 'TEACHING_ASSISTANT', exam: true, foreignEditor: true, futureEnd: false, suffix: 'for-exam-assessment' },
        { label: 'editor before the exam ends', role: 'EDITOR', exam: true, foreignEditor: false, futureEnd: true, suffix: 'for-exam-management' },
        { label: 'instructor before the exam ends', role: 'INSTRUCTOR', exam: true, foreignEditor: false, futureEnd: true, suffix: 'for-exam-management' },
        { label: 'course tutor', role: 'TEACHING_ASSISTANT', exam: false, foreignEditor: true, futureEnd: false, suffix: 'for-staff' },
    ])('loads and renders statistics for the $label using the owning course capability', async ({ role, exam, foreignEditor, futureEnd, suffix }) => {
        const courseId = 7;
        const exerciseId = 42;
        const params = { courseId: String(courseId), exerciseId: String(exerciseId), ...(exam ? { examId: '9', exerciseGroupId: '8' } : {}) };
        await TestBed.configureTestingModule({
            imports: [ExerciseStatisticsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute(params) },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(WebsocketService),
                MockProvider(FeatureToggleService),
                MockProvider(EntityTitleService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ExerciseStatisticsComponent, {
                remove: { imports: [StatisticsSpanSelectorComponent, ExerciseDetailStatisticsComponent, StatisticsScoreDistributionGraphComponent, StatisticsGraphComponent] },
                add: {
                    imports: [
                        MockComponent(StatisticsSpanSelectorComponent),
                        MockComponent(ExerciseDetailStatisticsComponent),
                        MockComponent(StatisticsScoreDistributionGraphComponent),
                        MockComponent(StatisticsGraphComponent),
                    ],
                },
            })
            .compileComponents();
        const user = new User();
        user.id = 1;
        user.login = 'statistics-user';
        user.authorities = [Authority.STUDENT];
        user.courseRoles = [{ courseId, roles: [role] }, ...(foreignEditor ? [{ courseId: 99, roles: ['EDITOR'] }] : [])];
        const account = TestBed.inject(AccountService);
        account.userIdentity.set(user);
        // These are the real course-scoped helpers: an editor in course99 cannot elevate the course7 read.
        expect(account.isAtLeastEditorInCourseWithId(courseId)).toBe(role === 'EDITOR' || role === 'INSTRUCTOR');
        const fixture = TestBed.createComponent(ExerciseStatisticsComponent);
        http = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        const exerciseRequest = http.expectOne((request) => request.url.startsWith(`api/exercise/exercises/${exerciseId}/`));
        const statisticsRequest = http.expectOne((request) => request.url === 'api/core/management/statistics/exercise-statistics');
        expect(statisticsRequest.request.method).toBe('GET');
        expect(statisticsRequest.request.params.keys()).toEqual(['exerciseId']);
        expect(statisticsRequest.request.params.get('exerciseId')).toBe(String(exerciseId));
        statisticsRequest.flush({
            averageScoreOfExercise: 50,
            maxPointsOfExercise: 10,
            numberOfExerciseScores: 10,
            scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
            numberOfParticipations: 10,
            numberOfStudentsOrTeamsInCourse: 10,
            numberOfPosts: 4,
            numberOfResolvedPosts: 2,
        });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#statistics-page-heading')).toBeNull();
        const course = { id: courseId, title: 'Statistics course' };
        const endDate = (futureEnd ? dayjs().add(1, 'day') : dayjs().subtract(1, 'day')).toISOString();
        exerciseRequest.flush({
            id: exerciseId,
            type: ExerciseType.TEXT,
            title: 'Statistics exercise',
            ...(exam ? { exerciseGroup: { id: 8, exam: { id: 9, course, endDate } } } : { course }),
        });
        await fixture.whenStable();
        fixture.detectChanges();
        expect(exerciseRequest.request.method).toBe('GET');
        expect(exerciseRequest.request.params.keys()).toEqual([]);
        expect(exerciseRequest.request.url).toBe(`api/exercise/exercises/${exerciseId}/${suffix}`);
        expect(fixture.nativeElement.querySelector('#statistics-page-heading')).not.toBeNull();
        expect(fixture.componentInstance.exercise()?.id).toBe(exerciseId);
        expect(fixture.componentInstance.course()?.id).toBe(courseId);
        expect(fixture.componentInstance.exerciseStatistics()).toMatchObject({ participationsInPercent: 100, resolvedPostsInPercent: 50, absoluteAveragePoints: 5 });
        const graph = fixture.debugElement.query(By.directive(StatisticsScoreDistributionGraphComponent)).componentInstance as StatisticsScoreDistributionGraphComponent;
        expect(graph.exerciseId()).toBe(exerciseId);
        expect(graph.courseId()).toBe(courseId);
        expect(graph.numberOfExerciseScores()).toBe(10);
        expect(graph.scoreDistribution()).toEqual([5, 0, 0, 0, 0, 0, 0, 0, 0, 5]);
        fixture.destroy();
    });
});
