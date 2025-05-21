import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseLearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';

describe('CourseLearnerProfileComponent', () => {
    let fixture: ComponentFixture<CourseLearnerProfileComponent>;
    let component: CourseLearnerProfileComponent;
    let selector: HTMLSelectElement;
    let httpTesting: HttpTestingController;

    let courseManagementService: CourseManagementService;
    let learnerProfileApiService: LearnerProfileApiService;

    let putUpdatedCourseLearnerProfileSpy: jest.SpyInstance;

    const errorBody = {
        entityName: 'courseLearnerProfile',
        errorKey: 'courseLearnerProfileNotFound',
        type: 'https://www.jhipster.tech/problem/problem-with-message',
        title: 'CourseLearnerProfile not found.',
        status: 400,
        skipAlert: true,
        message: 'error.courseLearnerProfileNotFound',
        params: 'courseLearnerProfile',
    };
    const errorHeaders = {
        'x-artemisapp-error': 'error.courseLearnerProfileNotFound',
        'x-artemisapp-params': 'courseLearnerProfile',
    };
    const course1: Course = {
        id: 1,
        title: 'Course 1',
    };
    const course2: Course = {
        id: 2,
        title: 'Course 2',
    };
    const courses = [course1, course2];

    const clp1 = new CourseLearnerProfileDTO();
    clp1.id = 1;
    clp1.courseId = 1;
    clp1.courseTitle = 'Course 1';
    clp1.aimForGradeOrBonus = 0;
    clp1.timeInvestment = 0;
    clp1.repetitionIntensity = 0;

    const clp2 = new CourseLearnerProfileDTO();
    clp2.id = 2;
    clp2.courseId = 2;
    clp2.courseTitle = 'Course 2';
    clp2.aimForGradeOrBonus = 1;
    clp2.timeInvestment = 1;
    clp2.repetitionIntensity = 1;
    const profiles = [clp1, clp2];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(AlertService),
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);

        courseManagementService = TestBed.inject(CourseManagementService);
        httpTesting = TestBed.inject(HttpTestingController);

        fixture = TestBed.createComponent(CourseLearnerProfileComponent);
        component = fixture.componentInstance;
        selector = fixture.nativeElement.getElementsByTagName('select')[0];

        jest.spyOn(courseManagementService, 'find').mockImplementation((courseId) => {
            if (courseId < 1 || courseId > 2) {
                return of(new HttpResponse<Course>({ status: 200 }));
            }

            return of(
                new HttpResponse<Course>({
                    status: 200,
                    body: courses[courseId - 1],
                }),
            );
        });

        jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockResolvedValue(profiles as CourseLearnerProfileDTO[]);

        putUpdatedCourseLearnerProfileSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedCourseLearnerProfile');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    function selectCourse(id: number) {
        fixture.detectChanges();
        Array.from(selector.options).forEach((opt) => {
            opt.selected = opt.value === String(id);
        });
    }

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseLearnerProfiles).toEqual(profiles);
    });

    it('should select active course', () => {
        const course = 1;
        selectCourse(course);
        const changeEvent = new Event('change', { bubbles: true, cancelable: false });
        selector.dispatchEvent(changeEvent);
        expect(component.activeCourseId).toBe(course);
    });

    function setupUpdateTest(courseIndex: number, courseId: number, mockUpdate: boolean): CourseLearnerProfileDTO {
        const newProfile = new CourseLearnerProfileDTO();
        Object.assign(newProfile, { ...profiles[courseIndex] });
        newProfile.repetitionIntensity = 1;
        newProfile.aimForGradeOrBonus = 2;
        newProfile.timeInvestment = 3;

        // Inject into component state
        const currentProfiles = component.courseLearnerProfiles();
        currentProfiles[courseIndex] = newProfile;
        component.courseLearnerProfiles.set(currentProfiles);
        component.activeCourseId = courseId;

        if (mockUpdate) {
            putUpdatedCourseLearnerProfileSpy.mockResolvedValue(newProfile);
        }

        component.onToggleChange();

        return newProfile;
    }

    function validateUpdate(index: number, profile: CourseLearnerProfileDTO) {
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.courseLearnerProfiles[index]).toEqual(profile);
    }

    function validateError(courseId: number, index: number, profile: CourseLearnerProfileDTO) {
        const req = httpTesting.expectOne(`api/atlas/course-learner-profiles/${courseId}`, 'Request to put new Profile');
        req.flush(errorBody, {
            headers: errorHeaders,
            status: 400,
            statusText: 'Bad Request',
        });
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.courseLearnerProfiles[index]).toEqual(profiles[index]);
    }

    describe('Making put requests', () => {
        it('should update profile on successful request', () => {
            const courseIndex = 1;
            const profile = setupUpdateTest(courseIndex, profiles[courseIndex].courseId, true);
            validateUpdate(courseIndex, profile);
        });

        it('should error on bad request', () => {
            const courseIndex = 1;
            const profile = setupUpdateTest(courseIndex, profiles[courseIndex].courseId, false);
            validateError(profiles[courseIndex].courseId, courseIndex, profile);
        });
    });
});
