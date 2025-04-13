import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute, Data } from '@angular/router';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { of } from 'rxjs';
import { CourseDetailComponent } from 'app/core/course/manage/detail/course-detail.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import dayjs from 'dayjs/esm';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseDetailDoughnutChartComponent } from 'app/core/course/manage/detail/course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from 'app/core/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementDetailViewDto } from 'app/core/course/shared/entities/course-management-detail-view-dto.model';
import { UsersImportButtonComponent } from 'app/shared/user-import/button/users-import-button.component';
import { EventManager } from 'app/shared/service/event-manager.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FullscreenComponent } from 'app/modeling/shared/fullscreen/fullscreen.component';

describe('Course Management Detail Component', () => {
    let component: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;
    let courseManagementService: CourseManagementService;
    let eventManager: EventManager;

    const course: Course = {
        id: 123,
        title: 'Course Title',
        description: 'Cras mattis iudicium purus sit amet fermentum. Gallia est omnis divisa in partes tres, quarum.',
        isAtLeastInstructor: true,
        endDate: dayjs().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
    };
    const dtoMock: CourseManagementDetailViewDto = {
        numberOfStudentsInCourse: 100,
        numberOfTeachingAssistantsInCourse: 5,
        numberOfEditorsInCourse: 5,
        numberOfInstructorsInCourse: 10,
        // assessments
        currentPercentageAssessments: 50,
        currentAbsoluteAssessments: 10,
        currentMaxAssessments: 20,
        // complaints
        currentPercentageComplaints: 60,
        currentAbsoluteComplaints: 6,
        currentMaxComplaints: 10,
        // feedback Request
        currentPercentageMoreFeedbacks: 70,
        currentAbsoluteMoreFeedbacks: 14,
        currentMaxMoreFeedbacks: 20,
        // average score
        currentPercentageAverageScore: 90,
        currentAbsoluteAverageScore: 90,
        currentMaxAverageScore: 100,
        activeStudents: [4, 10, 14, 35],
        // LLM
        currentTotalLlmCostInEur: 82.3,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                CourseDetailComponent,
                MockComponent(SecuredImageComponent),
                MockComponent(UsersImportButtonComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExamArchiveButtonComponent),
                MockComponent(CourseDetailDoughnutChartComponent),
                MockComponent(CourseDetailLineChartComponent),
                MockComponent(FullscreenComponent),
                MockDirective(FeatureToggleLinkDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: {
                            subscribe: (fn: (value: Data) => void) =>
                                fn({
                                    course,
                                }),
                        },
                        params: of({ courseId: course.id }),
                    },
                },
                MockProvider(CourseManagementService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(EventManager),

                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        component = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        eventManager = fixture.debugElement.injector.get(EventManager);
    });

    beforeEach(fakeAsync(() => {
        const statsStub = jest.spyOn(courseManagementService, 'getCourseStatisticsForDetailView');
        statsStub.mockReturnValue(of(new HttpResponse({ body: dtoMock })));
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call registerChangeInCourses on init', async () => {
        const registerSpy = jest.spyOn(component, 'registerChangeInCourses');
        component.ngOnInit();
        await Promise.resolve();
        await Promise.resolve();
        expect(component.courseDTO).toEqual(dtoMock);
        expect(component.course).toEqual(course);
        expect(registerSpy).toHaveBeenCalledOnce();
    });

    it('should destroy event subscriber onDestroy', () => {
        const destroySpy = jest.spyOn(eventManager, 'destroy');
        component.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    it.each([false, true])(`should return correct course-details with different settings enabled`, (allSettingsEnabled) => {
        component.course = course;
        if (allSettingsEnabled) {
            component.ltiEnabled = true;
            component.course.complaintsEnabled = true;
            component.course.requestMoreFeedbackEnabled = true;
            component.course.enrollmentEnabled = true;
            component.course.unenrollmentEnabled = true;
            component.course.organizations = [{ id: 32, name: 'TUM' }];
        }
        component.getCourseDetailSections();
        for (const section of component.courseDetailSections) {
            expect(section.headline).toBeTruthy();
            for (const detail of section.details) {
                expect(detail).toBeTruthy();
            }
        }
    });
});
