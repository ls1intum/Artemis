import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { CourseDashboardComponent } from 'app/core/course/overview/course-dashboard/course-dashboard.component';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { CourseExerciseLatenessComponent } from 'app/core/course/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';
import { CourseExercisePerformanceComponent } from 'app/core/course/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';

describe('CourseDashboardComponent', () => {
    let component: CourseDashboardComponent;
    let fixture: ComponentFixture<CourseDashboardComponent>;
    let debugElement: DebugElement;
    let courseStorageService: CourseStorageService;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [
                CourseDashboardComponent,
                MockComponent(CourseChatbotComponent),
                NgbProgressbar,
                TranslatePipeMock,
                MockComponent(CourseExerciseLatenessComponent),
                MockComponent(CourseExercisePerformanceComponent),
                MockDirective(FeatureToggleDirective),
                MockDirective(FeatureToggleHideDirective),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ course: '123' }) },
                LocalStorageService,
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: CourseStorageService,
                    useValue: {
                        getCourse: () => ({ id: 123, studentCourseAnalyticsDashboardEnabled: true, irisEnabledInCourse: true, learningPathsEnabled: true }),
                        subscribeToCourseUpdates: () => ({ subscribe: jest.fn() }),
                    },
                },
            ],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(CourseDashboardComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
        courseStorageService = TestBed.inject(CourseStorageService);
        fixture.detectChanges();
        component.isLoading = false;
    });

    it('should display chatbot when iris is enabled', () => {
        fixture.changeDetectorRef.detectChanges();

        const chatContainer = debugElement.query(By.css('.chat-container'));
        expect(chatContainer).toBeTruthy();
    });

    it('should show loading spinner when isLoading is true', () => {
        component.isLoading = true;
        fixture.changeDetectorRef.detectChanges();

        const spinner = debugElement.query(By.css('.spinner-border'));
        expect(spinner).toBeTruthy();
    });

    it('should show learning paths button if course has learningPathsEnabled', () => {
        component.atlasEnabled = true;
        fixture.changeDetectorRef.detectChanges();

        const learningPathButton = debugElement.query(By.css('button.btn-primary'));
        expect(learningPathButton).toBeTruthy();
        expect(learningPathButton.attributes['jhiTranslate']).toBe('artemisApp.studentAnalyticsDashboard.button.showLearningPath');
    });

    it('should navigate to learning paths when button is clicked', () => {
        component.atlasEnabled = true;
        fixture.changeDetectorRef.detectChanges();

        const navigateSpy = jest.spyOn(component, 'navigateToLearningPaths');
        const learningPathButton = debugElement.query(By.css('.btn.btn-primary'));

        learningPathButton.triggerEventHandler('click', null);
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should display competency accordion title when hasCompetencies is true', () => {
        component.hasCompetencies = true;
        fixture.changeDetectorRef.detectChanges();

        const competencyTitle = debugElement.query(By.css('h3'));
        expect(competencyTitle).toBeTruthy();
        expect(competencyTitle.attributes['jhiTranslate']).toBe('artemisApp.studentAnalyticsDashboard.competencyAccordion.title');
    });

    it('should show no data message when hasCompetencies is false', () => {
        component.hasCompetencies = false;
        fixture.changeDetectorRef.detectChanges();

        const noDataMessage = debugElement.query(By.css('[jhiTranslate="artemisApp.studentAnalyticsDashboard.competencyAccordion.noData"]'));
        expect(noDataMessage).toBeTruthy();
    });

    it('should not load course metrics when studentCourseAnalyticsDashboardEnabled is false', () => {
        const metricsSpy = jest.spyOn(component, 'loadMetrics');
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            id: 456,
            studentCourseAnalyticsDashboardEnabled: false,
            irisEnabledInCourse: true,
            learningPathsEnabled: true,
        });
        component.ngOnInit();
        expect(metricsSpy).not.toHaveBeenCalled();
    });
    it('should load course metrics when studentCourseAnalyticsDashboardEnabled is true', () => {
        const metricsSpy = jest.spyOn(component, 'loadMetrics');
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            id: 456,
            studentCourseAnalyticsDashboardEnabled: true,
            irisEnabledInCourse: true,
            learningPathsEnabled: true,
        });
        component.ngOnInit();
        expect(metricsSpy).toHaveBeenCalledOnce();
    });
    it('should correctly calculate overall performance', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { id: 1, maxPoints: 10 },
                2: { id: 2, maxPoints: 20 },
                3: { id: 3, maxPoints: 30 },
            },
            score: {
                1: 80,
                2: 50,
                3: 100,
            },
        };

        const exerciseIds = [1, 2, 3];
        (component as any).setOverallPerformance(exerciseIds, exerciseMetrics);

        expect(component.points).toBeCloseTo(48.0, 1);
        expect(component.maxPoints).toBeCloseTo(60.0, 1);
        expect(component.progress).toBeCloseTo(80.0, 1);
    });

    it('should correctly map exercise performance data', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
                2: { title: 'Exercise 2', shortName: 'E2' },
            },
            score: {
                1: 90,
                2: 50,
            },
            averageScore: {
                1: 70,
                2: 60,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExercisePerformance(sortedIds, exerciseMetrics);

        expect(component.exercisePerformance!).toEqual([
            {
                exerciseId: 1,
                title: 'Exercise 1',
                shortName: 'E1',
                score: 90,
                averageScore: 70,
            },
            {
                exerciseId: 2,
                title: 'Exercise 2',
                shortName: 'E2',
                score: 50,
                averageScore: 60,
            },
        ]);
    });

    it('should skip exercises without information in exercise performance', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
            },
            score: {
                1: 100,
                2: 50,
            },
            averageScore: {
                1: 80,
                2: 60,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExercisePerformance(sortedIds, exerciseMetrics);

        expect(component.exercisePerformance!).toHaveLength(1);
        expect(component.exercisePerformance![0].exerciseId).toBe(1);
    });

    it('should correctly map exercise lateness data', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
                2: { title: 'Exercise 2', shortName: 'E2' },
            },
            latestSubmission: {
                1: -0.2,
                2: 0.5,
            },
            averageLatestSubmission: {
                1: -0.1,
                2: 0.4,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExerciseLateness(sortedIds, exerciseMetrics);

        expect(component.exerciseLateness!).toEqual([
            {
                exerciseId: 1,
                title: 'Exercise 1',
                shortName: 'E1',
                relativeLatestSubmission: -0.2,
                relativeAverageLatestSubmission: -0.1,
            },
            {
                exerciseId: 2,
                title: 'Exercise 2',
                shortName: 'E2',
                relativeLatestSubmission: 0.5,
                relativeAverageLatestSubmission: 0.4,
            },
        ]);
    });

    it('should skip exercises without information in exercise lateness', () => {
        const exerciseMetrics = {
            exerciseInformation: {
                1: { title: 'Exercise 1', shortName: 'E1' },
            },
            latestSubmission: {
                1: -0.3,
                2: 0.6,
            },
            averageLatestSubmission: {
                1: -0.2,
                2: 0.5,
            },
        };

        const sortedIds = [1, 2];
        (component as any).setExerciseLateness(sortedIds, exerciseMetrics);

        expect(component.exerciseLateness!).toHaveLength(1);
        expect(component.exerciseLateness![0].exerciseId).toBe(1);
    });

    it('should handle empty input gracefully in exercise lateness', () => {
        const exerciseMetrics = {
            exerciseInformation: {},
            latestSubmission: {},
            averageLatestSubmission: {},
        };

        const sortedIds: number[] = [];
        (component as any).setExerciseLateness(sortedIds, exerciseMetrics);

        expect(component.exerciseLateness!).toEqual([]);
    });
});
