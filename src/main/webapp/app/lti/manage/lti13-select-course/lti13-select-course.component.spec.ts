import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { LtiCoursesComponent } from 'app/lti/manage/lti13-select-course/lti13-select-course.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { LtiCourseCardComponent } from 'app/lti/manage/lti-course-card/lti-course-card.component';
import { OnlineCourseDtoModel } from 'app/lti/shared/entities/online-course-dto.model';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';

describe('LtiCoursesComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LtiCoursesComponent;
    let fixture: ComponentFixture<LtiCoursesComponent>;
    let courseManagementService: CourseManagementService;
    let sessionStorageService: SessionStorageService;
    let alertService: AlertService;
    let sessionStorageRetrieveSpy: ReturnType<typeof vi.spyOn>;

    const mockCourses: OnlineCourseDtoModel[] = [
        { id: 1, title: 'Course A', shortName: 'cA', registrationId: '1' },
        { id: 2, title: 'Course B', shortName: 'cB', registrationId: '1' },
        { id: 3, title: 'Course C', shortName: 'cC', registrationId: '1' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LtiCoursesComponent, MockComponent(LtiCourseCardComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CourseManagementService, {
                    findAllOnlineCoursesWithRegistrationId: vi.fn().mockReturnValue(of(mockCourses)),
                }),
                SessionStorageService,
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: { params: of({}) } },
            ],
        }).compileComponents();

        courseManagementService = TestBed.inject(CourseManagementService);
        sessionStorageService = TestBed.inject(SessionStorageService);
        alertService = TestBed.inject(AlertService);
        sessionStorageRetrieveSpy = vi.spyOn(sessionStorageService, 'retrieve');
        fixture = TestBed.createComponent(LtiCoursesComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should load and filter courses on ngOnInit', async () => {
        sessionStorageRetrieveSpy.mockReturnValue('1');

        component.ngOnInit();
        await fixture.whenStable();

        expect(courseManagementService.findAllOnlineCoursesWithRegistrationId).toHaveBeenCalledWith('1');
        expect(component.courses).toHaveLength(3);
        expect(component.courses[0].title).toBe('Course A');
    });

    it('should set courses to empty array when clientId is null', async () => {
        sessionStorageRetrieveSpy.mockReturnValue(null);

        component.ngOnInit();
        await fixture.whenStable();

        expect(courseManagementService.findAllOnlineCoursesWithRegistrationId).not.toHaveBeenCalled();
        expect(component.courses).toEqual([]);
    });

    it('should handle error when loading courses fails', async () => {
        sessionStorageRetrieveSpy.mockReturnValue('1');
        const errorSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(courseManagementService, 'findAllOnlineCoursesWithRegistrationId').mockReturnValue(throwError(() => ({ message: 'Network error' })));

        component.ngOnInit();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledWith('error.unexpectedError', {
            error: 'Network error',
        });
    });

    it('should call loadAndFilterCourses on ngOnInit', () => {
        const loadAndFilterCoursesSpy = vi.spyOn(component, 'loadAndFilterCourses');
        sessionStorageRetrieveSpy.mockReturnValue('1');

        component.ngOnInit();

        expect(loadAndFilterCoursesSpy).toHaveBeenCalled();
    });

    it('should properly retrieve courses with correct registration id', async () => {
        sessionStorageRetrieveSpy.mockReturnValue('registration-123');
        vi.spyOn(courseManagementService, 'findAllOnlineCoursesWithRegistrationId').mockReturnValue(of(mockCourses));

        component.loadAndFilterCourses();
        await fixture.whenStable();

        expect(courseManagementService.findAllOnlineCoursesWithRegistrationId).toHaveBeenCalledWith('registration-123');
        expect(component.courses).toEqual(mockCourses);
    });
});
