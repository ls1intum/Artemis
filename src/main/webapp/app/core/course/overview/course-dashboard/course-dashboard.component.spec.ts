import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { CourseDashboardComponent } from 'app/core/course/overview/course-dashboard/course-dashboard.component';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
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
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: CourseStorageService,
                    useValue: {
                        getCourse: () => ({ id: 123, studentCourseAnalyticsDashboardEnabled: true, irisCourseChatEnabled: true, learningPathsEnabled: true }),
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
        fixture.detectChanges();
        component.isLoading = false;
    });

    it('should display chatbot when iris is enabled', () => {
        fixture.detectChanges();

        const chatContainer = debugElement.query(By.css('.chat-container'));
        expect(chatContainer).toBeTruthy();
    });

    it('should show loading spinner when isLoading is true', () => {
        component.isLoading = true;
        fixture.detectChanges();

        const spinner = debugElement.query(By.css('.spinner-border'));
        expect(spinner).toBeTruthy();
    });

    it('should show learning paths button if course has learningPathsEnabled', () => {
        component.atlasEnabled = true;
        fixture.detectChanges();

        const learningPathButton = debugElement.query(By.css('button.btn-primary'));
        expect(learningPathButton).toBeTruthy();
        expect(learningPathButton.attributes['jhiTranslate']).toBe('artemisApp.studentAnalyticsDashboard.button.showLearningPath');
    });

    it('should navigate to learning paths when button is clicked', () => {
        component.atlasEnabled = true;
        fixture.detectChanges();

        const navigateSpy = jest.spyOn(component, 'navigateToLearningPaths');
        const learningPathButton = debugElement.query(By.css('.btn.btn-primary'));

        learningPathButton.triggerEventHandler('click', null);
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should display competency accordion title when hasCompetencies is true', () => {
        component.hasCompetencies = true;
        fixture.detectChanges();

        const competencyTitle = debugElement.query(By.css('h3'));
        expect(competencyTitle).toBeTruthy();
        expect(competencyTitle.attributes['jhiTranslate']).toBe('artemisApp.studentAnalyticsDashboard.competencyAccordion.title');
    });

    it('should show no data message when hasCompetencies is false', () => {
        component.hasCompetencies = false;
        fixture.detectChanges();

        const noDataMessage = debugElement.query(By.css('[jhiTranslate="artemisApp.studentAnalyticsDashboard.competencyAccordion.noData"]'));
        expect(noDataMessage).toBeTruthy();
    });

    it('should not load course metrics when studentCourseAnalyticsDashboardEnabled is false', () => {
        const metricsSpy = jest.spyOn(component, 'loadMetrics');
        component.ngOnInit();
        expect(metricsSpy).not.toHaveBeenCalled();
    });
    it('should load course metrics when studentCourseAnalyticsDashboardEnabled is true', () => {
        const metricsSpy = jest.spyOn(component, 'loadMetrics');
        component.course = { id: 456, studentCourseAnalyticsDashboardEnabled: true, irisCourseChatEnabled: true, learningPathsEnabled: true };
        component.ngOnInit();
        expect(metricsSpy).toHaveBeenCalledOnce();
    });
});
