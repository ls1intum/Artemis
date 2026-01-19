import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathInstructorPageComponent } from 'app/atlas/manage/learning-path-instructor-page/learning-path-instructor-page.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathInstructorPageComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LearningPathInstructorPageComponent;
    let fixture: ComponentFixture<LearningPathInstructorPageComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let courseManagementService: CourseManagementService;
    let getCourseSpy: ReturnType<typeof vi.spyOn>;

    const courseId = 1;

    const course = <Course>{
        id: 1,
        learningPathsEnabled: false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathInstructorPageComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: courseId,
                            }),
                        },
                    },
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: AlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);
        courseManagementService = TestBed.inject(CourseManagementService);

        getCourseSpy = vi.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(
            of(
                new HttpResponse({
                    body: course,
                }),
            ),
        );

        fixture = TestBed.createComponent(LearningPathInstructorPageComponent);
        component = fixture.componentInstance;
    });

    it('should load course', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCourseSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.learningPathsEnabled()).toBe(course.learningPathsEnabled);
    });

    it('should show error on load course', async () => {
        const alertServiceErrorSpy = vi.spyOn(alertService, 'addAlert');
        getCourseSpy.mockRejectedValue(new HttpErrorResponse({ error: 'Error' }));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly on course loading', async () => {
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show enable learning paths button if not enabled', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const enableLearningPathsButton = fixture.nativeElement.querySelector('#enable-learning-paths-button');
        expect(enableLearningPathsButton).toBeDefined();
    });

    it('should enable learning paths', async () => {
        const enableLearningPathsSpy = vi.spyOn(learningPathApiService, 'enableLearningPaths').mockResolvedValue();

        fixture.detectChanges();
        await component.enableLearningPaths();

        expect(enableLearningPathsSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.learningPathsEnabled()).toBeTrue();
    });

    it('should show error on enable learning paths', async () => {
        const alertServiceErrorSpy = vi.spyOn(alertService, 'addAlert');
        vi.spyOn(learningPathApiService, 'enableLearningPaths').mockRejectedValue(new Error('Error'));
        fixture.detectChanges();
        await component.enableLearningPaths();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly on enable learning paths', async () => {
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');
        vi.spyOn(learningPathApiService, 'enableLearningPaths').mockResolvedValue();

        await component.enableLearningPaths();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });
});
