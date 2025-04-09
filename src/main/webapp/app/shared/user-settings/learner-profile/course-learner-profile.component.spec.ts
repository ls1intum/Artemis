import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseLearnerProfileComponent } from 'app/shared/user-settings/learner-profile/course-learner-profile.component';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { Course } from 'app/core/shared/entities/course.model';
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

    const clp1: CourseLearnerProfileDTO = {
        id: 1,
        courseId: 1,
        aimForGradeOrBonus: 0,
        timeInvestment: 0,
        repetitionIntensity: 0,
    };
    const clp2: CourseLearnerProfileDTO = {
        id: 2,
        courseId: 2,
        aimForGradeOrBonus: 1,
        timeInvestment: 1,
        repetitionIntensity: 1,
    };
    const profiles = { [clp1.courseId]: clp1, [clp2.courseId]: clp2 };

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

        jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockReturnValue(
            new Promise<Record<number, CourseLearnerProfileDTO>>((resolve) => resolve(profiles)),
        );

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
        expect(component.courses).toStrictEqual(courses);
        expect(component.courseLearnerProfiles).toEqual(profiles);
    });

    it('should select active course', () => {
        const course = 1;
        selectCourse(course);
        const changeEvent = new Event('change', { bubbles: true, cancelable: false });
        selector.dispatchEvent(changeEvent);
        expect(component.activeCourse).toBe(course);
    });

    function setupUpdateTest(course: number): CourseLearnerProfileDTO {
        const newProfile = profiles[course];
        newProfile['repetitionIntensity'] = 1;
        newProfile['aimForGradeOrBonus'] = 2;
        newProfile['timeInvestment'] = 3;
        component.activeCourse = course;

        component.update();

        return newProfile;
    }

    function validateUpdate(course: number, profile: CourseLearnerProfileDTO) {
        const req = httpTesting.expectOne(`api/atlas/course-learner-profiles/${course}`, 'Request to put new Profile');
        req.flush(profile);
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.courseLearnerProfiles[course]).toEqual(profile);
    }

    function validateError(course: number, profile: CourseLearnerProfileDTO) {
        const req = httpTesting.expectOne(`api/atlas/course-learner-profiles/${course}`, 'Request to put new Profile');
        req.flush(errorBody, {
            headers: errorHeaders,
            status: 400,
            statusText: 'Bad Request',
        });
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.courseLearnerProfiles[course]).toEqual(profiles[course]);
    }

    describe('Making put requests', () => {
        it('should update profile on successful request', () => {
            const course = 1;
            const profile = setupUpdateTest(course);
            validateUpdate(course, profile);
        });

        it('should error on bad request', () => {
            const course = 1;
            const profile = setupUpdateTest(course);
            validateError(course, profile);
        });
    });
});
