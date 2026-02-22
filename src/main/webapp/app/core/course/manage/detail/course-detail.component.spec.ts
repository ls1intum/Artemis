import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Data } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { CourseDetailComponent } from 'app/core/course/manage/detail/course-detail.component';
import { MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementDetailViewDto } from 'app/core/course/shared/entities/course-management-detail-view-dto.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';

describe('Course Management Detail Component', () => {
    setupTestBed({ zoneless: true });

    let component: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;
    let courseManagementService: CourseManagementService;
    let eventManager: EventManager;
    let irisSettingsService: IrisSettingsService;
    let profileService: ProfileService;

    const course: Course = {
        id: 123,
        title: 'Course Title',
        description: 'Cras mattis iudicium purus sit amet fermentum. Gallia est omnis divisa in partes tres, quarum.',
        endDate: dayjs().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
    };
    const dtoMock: CourseManagementDetailViewDto = {
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
        // LLM
        currentTotalLlmCostInEur: 82.3,
    };
    const courseDataSubject = new BehaviorSubject<Data>({ course: { ...course } });
    const mockActivatedRoute = {
        data: courseDataSubject.asObservable(),
        params: of({ courseId: course.id }),
    } as unknown as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseDetailComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
                MockProvider(CourseManagementService),
                MockProvider(OrganizationManagementService, {
                    getOrganizationsByCourse: () => of([]),
                }),
                MockProvider(IrisSettingsService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(EventManager),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        component = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        eventManager = TestBed.inject(EventManager);
        courseDataSubject.next({ course });
    });

    beforeEach(() => {
        const statsStub = vi.spyOn(courseManagementService, 'getCourseStatisticsForDetailView');
        statsStub.mockReturnValue(of(new HttpResponse({ body: dtoMock })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should make iris settings call when instructor', async () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: ['iris'] } as ProfileInfo);
        courseDataSubject.next({ course: { ...course, isAtLeastInstructor: true } });
        const irisSpy = vi
            .spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit')
            .mockReturnValue(of({ courseId: 123, settings: { enabled: true, variant: 'default', rateLimit: {} } } as IrisCourseSettingsWithRateLimitDTO));
        await component.ngOnInit();
        expect(irisSpy).toHaveBeenCalledOnce();
    });

    it('should not make iris settings call when not instructor', async () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: ['iris'] } as ProfileInfo);
        courseDataSubject.next({ course: { ...course, isAtLeastEditor: true } });
        const irisSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit');
        await component.ngOnInit();
        expect(irisSpy).not.toHaveBeenCalled();
    });

    it('should call registerChangeInCourses on init', async () => {
        const registerSpy = vi.spyOn(component, 'registerChangeInCourses');
        component.ngOnInit();
        await Promise.resolve();
        await Promise.resolve();
        expect(component.courseDTO()).toEqual(dtoMock);
        // Course will have organizations added from the mocked service
        expect(component.course()).toEqual({ ...course, organizations: [] });
        expect(registerSpy).toHaveBeenCalledOnce();
    });

    it('should destroy event subscriber onDestroy', () => {
        const destroySpy = vi.spyOn(eventManager, 'destroy');
        component.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    it.each([false, true])(`should return correct course-details with different settings enabled`, (allSettingsEnabled) => {
        const testCourse = { ...course };
        if (allSettingsEnabled) {
            component.ltiEnabled.set(true);
            testCourse.complaintsEnabled = true;
            testCourse.requestMoreFeedbackEnabled = true;
            testCourse.enrollmentEnabled = true;
            testCourse.unenrollmentEnabled = true;
            testCourse.organizations = [{ id: 32, name: 'TUM' }];
        }
        component.course.set(testCourse);
        component.getCourseDetailSections();
        for (const section of component.courseDetailSections()) {
            expect(section.headline).toBeTruthy();
            for (const detail of section.details) {
                expect(detail).toBeTruthy();
            }
        }
    });
});
