import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Data, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementDetailViewDto } from 'app/course/shared/entities/course-management-detail-view-dto.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { Course } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('Course Management Detail Component', () => {
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
        queryParams: of({}),
        snapshot: { queryParamMap: convertToParamMap({}) },
    } as unknown as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseDetailComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
                MockProvider(CourseManagementService, {
                    getStatisticsData: () => of([]),
                    getAssessmentAttentionState: () => of(new HttpResponse({ body: { needsAttention: false } })),
                }),
                MockProvider(OrganizationManagementService, {
                    getOrganizationsByCourse: () => of([]),
                }),
                MockProvider(IrisSettingsService, {
                    refresh$: of(undefined),
                }),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(EventManager),
                MockProvider(Router),
                { provide: DialogService, useClass: MockDialogService },
            ],
            deferBlockBehavior: DeferBlockBehavior.Manual,
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
        // irisEnabled is computed once at construction time, so the profile mock must be in place before the component is (re-)created.
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: ['iris'] } as ProfileInfo);
        fixture = TestBed.createComponent(CourseDetailComponent);
        component = fixture.componentInstance;
        courseDataSubject.next({ course: { ...course, isAtLeastTutor: true, isAtLeastInstructor: true, onboardingDone: true } });
        const irisSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(
            of({
                courseId: 123,
                settings: {
                    enabled: true,
                    askUserModeEnabled: true,
                    variant: 'default',
                    askUserModeSettings: { minQuestions: 3, maxQuestions: 5, timeLimitQuestion: 20, timeLimitInClass: 15 },
                    rateLimit: {},
                },
            } as IrisCourseSettingsWithRateLimitDTO),
        );
        await component.ngOnInit();
        TestBed.flushEffects();
        expect(irisSpy).toHaveBeenCalledWith(123);
    });

    it('should make iris settings call when editor (but not instructor)', async () => {
        // irisEnabled is computed once at construction time, so the profile mock must be in place before the component is (re-)created.
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: ['iris'] } as ProfileInfo);
        fixture = TestBed.createComponent(CourseDetailComponent);
        component = fixture.componentInstance;
        courseDataSubject.next({ course: { ...course, isAtLeastTutor: true, isAtLeastEditor: true, onboardingDone: true } });
        const irisSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(
            of({
                courseId: 123,
                settings: {
                    enabled: true,
                    askUserModeEnabled: true,
                    variant: 'default',
                    askUserModeSettings: { minQuestions: 3, maxQuestions: 5, timeLimitQuestion: 20, timeLimitInClass: 15 },
                    rateLimit: {},
                },
            } as IrisCourseSettingsWithRateLimitDTO),
        );
        await component.ngOnInit();
        TestBed.flushEffects();
        expect(irisSpy).toHaveBeenCalledWith(123);
    });

    it('should not make iris settings call when not at least tutor', async () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: ['iris'] } as ProfileInfo);
        courseDataSubject.next({ course: { ...course, isAtLeastTutor: false } });
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
        // Course will have organizations added from the mocked service; setCourse() stores the route data as-is, it does not normalize it into a Course instance.
        const expectedCourse = { ...course, organizations: [] };
        expect(component.course()).toEqual(expectedCourse);
        expect(registerSpy).toHaveBeenCalledOnce();
    });

    it('should destroy event subscriber onDestroy', async () => {
        const mockSubscription = {} as any;
        vi.spyOn(eventManager, 'subscribe').mockReturnValue(mockSubscription);
        await component.ngOnInit();
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

    it('should keep the charts unmounted behind a geometry-bearing placeholder until the viewport defer block triggers', async () => {
        vi.spyOn(courseManagementService, 'getStatisticsData').mockReturnValue(of([]));
        component.course.set({ ...course, complaintsEnabled: true, requestMoreFeedbackEnabled: true, numberOfStudents: 42 });
        fixture.detectChanges();

        // Regression guard: the defer trigger must target a placeholder with real geometry, not an empty/zero-height sentinel.
        const placeholder = fixture.nativeElement.querySelector('div[style*="min-height: 350px"]');
        expect(placeholder).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-course-detail-line-chart')).toBeNull();

        const [chartsBlock] = await fixture.getDeferBlocks();
        expect(chartsBlock).toBeDefined();

        await chartsBlock.render(DeferBlockState.Complete);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('jhi-course-detail-doughnut-chart').length).toBeGreaterThan(0);
        expect(fixture.nativeElement.querySelector('jhi-course-detail-line-chart')).toBeTruthy();
    });
});
