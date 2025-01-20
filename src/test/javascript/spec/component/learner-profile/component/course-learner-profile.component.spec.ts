import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseLearnerProfileComponent } from 'app/shared/user-settings/learner-profile/course-learner-profile.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { LearnerProfileApiService } from 'app/atlas/service/learner-profile-api.service';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseLearnerProfileDTO } from 'app/entities/learner-profile.model';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';

describe('CourseLearnerProfileComponent', () => {
    let fixture: ComponentFixture<CourseLearnerProfileComponent>;
    let component: CourseLearnerProfileComponent;
    let selector: HTMLSelectElement;
    let httpTesting: HttpTestingController;

    let courseManagerService: CourseManagementService;
    let learnerProfileApiService: LearnerProfileApiService;

    let findAllForDropdownSpy: jest.SpyInstance;
    let getCourseLearnerProfilesForCurrentUserSpy: jest.SpyInstance;
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
    let errorHeaders = {
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

        courseManagerService = TestBed.inject(CourseManagementService);
        httpTesting = TestBed.inject(HttpTestingController);

        fixture = TestBed.createComponent(CourseLearnerProfileComponent);
        component = fixture.componentInstance;
        selector = fixture.nativeElement.getElementsByTagName('select')[0];

        findAllForDropdownSpy = jest.spyOn(courseManagerService, 'findAllForDropdown').mockReturnValue(
            of(
                new HttpResponse<Course[]>({
                    status: 200,
                    body: courses,
                }),
            ),
        );

        getCourseLearnerProfilesForCurrentUserSpy = jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockReturnValue(profiles);
        putUpdatedCourseLearnerProfileSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedCourseLearnerProfile');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    function selectCourse(id: number) {
        for (let opt of selector.options) {
            opt.selected = opt.value == String(id);
        }
    }

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courses).toStrictEqual(courses);
        expect(component.courseLearnerProfiles).toBe(profiles);
    });

    it('should select active course', () => {
        let course = 1;
        selectCourse(course);
        let changeEvent = new Event('change', { bubbles: true, cancelable: false });
        selector.dispatchEvent(changeEvent);
        expect(component.activeCourse).toBe(course);
    });

    function setupUpdateTest(course: number, updateFn: Function, attribute: keyof CourseLearnerProfileDTO): CourseLearnerProfileDTO {
        let newVal = (profiles[course][attribute] + 1) % 5;
        let newProfile = profiles[course];
        newProfile[attribute] = newVal;
        component.activeCourse = course;

        updateFn(newVal);
        return newProfile;
    }

    function validateUpdate(course: number, profile: CourseLearnerProfileDTO) {
        const req = httpTesting.expectOne(`api/learner-profiles/course-learner-profiles/${course}`, 'Request to put new Profile');
        req.flush(profile);
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toBe(profile);
        expect(component.courseLearnerProfiles[course]).toBe(profile);
    }

    function validateError(course: number, profile: CourseLearnerProfileDTO) {
        const req = httpTesting.expectOne(`api/learner-profiles/course-learner-profiles/${course}`, 'Request to put new Profile');
        req.flush(errorBody, {
            headers: errorHeaders,
            status: 400,
            statusText: 'Bad Request',
        });
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toBe(profile);
        expect(component.courseLearnerProfiles[course]).toBe(profiles[course]);
    }

    it('should update aimForGradeOrBonus on successful request', () => {
        let course = 1;
        let newProfile = setupUpdateTest(course, component.updateAimForGradeOrBonus.bind(component), 'aimForGradeOrBonus');
        validateUpdate(course, newProfile);
    });

    it('should not update aimForGradeOrBonus on error', () => {
        let course = 1;
        let newProfile = setupUpdateTest(course, component.updateAimForGradeOrBonus.bind(component), 'aimForGradeOrBonus');
        validateError(course, newProfile);
    });

    it('should update timeInvestment on successful request', () => {
        let course = 1;
        let newProfile = setupUpdateTest(course, component.updateTimeInvestment.bind(component), 'timeInvestment');
        validateUpdate(course, newProfile);
    });

    it('should not update timeInvestment on error', () => {
        let course = 1;
        let newProfile = setupUpdateTest(course, component.updateTimeInvestment.bind(component), 'timeInvestment');
        validateError(course, newProfile);
    });

    it('should update repetitionIntensity on successful request', () => {
        let course = 1;
        let newProfile = setupUpdateTest(course, component.updateRepetitionIntensity.bind(component), 'repetitionIntensity');
        validateUpdate(course, newProfile);
    });

    it('should not update repetitionIntensity on error', () => {
        let course = 1;
        let newProfile = setupUpdateTest(course, component.updateRepetitionIntensity.bind(component), 'repetitionIntensity');
        validateError(course, newProfile);
    });
});
