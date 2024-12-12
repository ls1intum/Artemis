import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathInstructorPageComponent } from 'app/course/learning-paths/pages/learning-path-instructor-page/learning-path-instructor-page.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../../test.module';
import { Course } from 'app/entities/course.model';

describe('LearningPathInstructorPageComponent', () => {
    let component: LearningPathInstructorPageComponent;
    let fixture: ComponentFixture<LearningPathInstructorPageComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let courseManagementService: CourseManagementService;
    let getCourseSpy: jest.SpyInstance;

    const courseId = 1;

    const course = <Course>{
        id: 1,
        learningPathsEnabled: false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, LearningPathInstructorPageComponent],
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
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);
        courseManagementService = TestBed.inject(CourseManagementService);

        getCourseSpy = jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(
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
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');
        getCourseSpy.mockRejectedValue(new HttpErrorResponse({ error: 'Error' }));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly on course loading', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

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
        const enableLearningPathsSpy = jest.spyOn(learningPathApiService, 'enableLearningPaths').mockResolvedValue();

        await clickEnableLearningPathsButton();

        expect(enableLearningPathsSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.learningPathsEnabled()).toBeTrue();
    });

    it('should show error on enable learning paths', async () => {
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');
        jest.spyOn(learningPathApiService, 'enableLearningPaths').mockRejectedValue(new Error('Error'));

        await clickEnableLearningPathsButton();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly on enable learning paths', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        await clickEnableLearningPathsButton();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    async function clickEnableLearningPathsButton(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();

        const enableLearningPathsButton = fixture.nativeElement.querySelector('#enable-learning-paths-button');
        enableLearningPathsButton.click();

        fixture.detectChanges();
        await fixture.whenStable();
    }
});
