import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseLearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/course-learner-profile-dto.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseLearnerProfileComponent', () => {
    let fixture: ComponentFixture<CourseLearnerProfileComponent>;
    let component: CourseLearnerProfileComponent;
    let selector: HTMLSelectElement;

    let courseManagementService: CourseManagementService;
    let learnerProfileApiService: LearnerProfileApiService;
    let alertService: AlertService;

    let putUpdatedCourseLearnerProfileSpy: jest.SpyInstance;

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
    clp1.aimForGradeOrBonus = 2;
    clp1.timeInvestment = 2;
    clp1.repetitionIntensity = 2;

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
                SessionStorageService,
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);
        alertService = TestBed.inject(AlertService);

        courseManagementService = TestBed.inject(CourseManagementService);

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
        expect(component.courseLearnerProfiles()).toEqual(profiles);
    });

    it('should select active course', () => {
        const course = 1;
        selectCourse(course);
        const changeEvent = new Event('change', { bubbles: true, cancelable: false });
        selector.dispatchEvent(changeEvent);
        expect(component.activeCourseId).toBe(course);
    });

    async function setupUpdateTest(courseIndex: number, courseId: number, mockUpdate: boolean): Promise<CourseLearnerProfileDTO> {
        const newProfile = new CourseLearnerProfileDTO();
        Object.assign(newProfile, Object.assign({}, profiles[courseIndex]));
        newProfile.repetitionIntensity = 1;
        newProfile.aimForGradeOrBonus = 2;
        newProfile.timeInvestment = 3;

        // Set up component state
        component.courseLearnerProfiles.set([...profiles]);
        component.activeCourseId = courseId;
        component.disabled = false;

        // Set the profile values in the component's signals
        component.aimForGradeOrBonus.set(newProfile.aimForGradeOrBonus);
        component.timeInvestment.set(newProfile.timeInvestment);
        component.repetitionIntensity.set(newProfile.repetitionIntensity);

        // Update the profile in the component's state
        const updatedProfiles = [...profiles];
        updatedProfiles[courseIndex] = newProfile;
        component.courseLearnerProfiles.set(updatedProfiles);

        if (mockUpdate) {
            putUpdatedCourseLearnerProfileSpy.mockResolvedValue(newProfile);
        } else {
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(new Error('Bad Request'));
        }

        await component.onToggleChange();
        await fixture.whenStable();
        fixture.detectChanges();

        return newProfile;
    }

    function validateUpdate(index: number, profile: CourseLearnerProfileDTO) {
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.courseLearnerProfiles()[index]).toEqual(profile);
    }

    async function validateError(courseId: number, index: number, profile: CourseLearnerProfileDTO) {
        await fixture.whenStable();
        expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedCourseLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.courseLearnerProfiles()[index]).toEqual(profile);
    }

    describe('Making put requests', () => {
        it('should update profile on successful request', async () => {
            const courseIndex = 1;
            const profile = await setupUpdateTest(courseIndex, profiles[courseIndex].courseId, true);
            validateUpdate(courseIndex, profile);
        });

        it('should error on bad request', async () => {
            const courseIndex = 1;
            const profile = await setupUpdateTest(courseIndex, profiles[courseIndex].courseId, false);
            await validateError(profiles[courseIndex].courseId, courseIndex, profile);
        });

        it('should not update profile when activeCourseId is null', async () => {
            // Arrange
            component.activeCourseId = null;
            component.disabled = false;

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
        });

        it('should not update profile when course profile is not found', async () => {
            // Arrange
            component.activeCourseId = 999; // Non-existent course ID
            component.disabled = false;

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
        });

        it('should show error when profile values are invalid', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Set invalid values (outside valid range)
            component.aimForGradeOrBonus.set(-1);
            component.timeInvestment.set(6);
            component.repetitionIntensity.set(7);

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
        });
    });

    describe('Error handling', () => {
        it('should handle non-HTTP error when loading profiles', async () => {
            // Arrange
            const genericError = new Error('Network error');
            jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockRejectedValue(genericError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which internally calls loadProfiles
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should handle HTTP error during profile update', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            const httpError = new HttpErrorResponse({
                error: { title: 'Server Error' },
                status: 500,
            });
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Server Error',
                disableTranslation: true,
            });
        });

        it('should handle HTTP error with headers during profile update', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            const httpError = new HttpErrorResponse({
                error: { title: 'Server Error' },
                status: 500,
                headers: new HttpHeaders().set('x-artemisapp-alert', 'Custom Error Message'),
            });
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert - handleError prioritizes error.error?.title over header
            expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Server Error',
                disableTranslation: true,
            });
        });

        it('should handle HTTP error with only header message during profile update', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            const httpError = new HttpErrorResponse({
                status: 500,
                headers: new HttpHeaders().set('x-artemisapp-alert', 'Custom Error Message'),
            });
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert - handleError uses header when no title is present
            expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Custom Error Message',
                disableTranslation: true,
            });
        });

        it('should handle HTTP error with title through API failure', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                error: { title: 'Custom error message' },
                status: 400,
            });
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state and trigger error through API call
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Custom error message',
                disableTranslation: true,
            });
        });

        it('should handle HTTP error with x-artemisapp-alert header through API failure', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                headers: new HttpHeaders().set('x-artemisapp-alert', 'Header error message'),
                status: 400,
            });
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state and trigger error through API call
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Header error message',
                disableTranslation: true,
            });
        });

        it('should handle generic error through API failure', async () => {
            // Arrange
            const genericError = new Error('Generic error');
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state and trigger error through API call
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(genericError);
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });
    });

    describe('Course selection', () => {
        it('should handle selection of no course', () => {
            // Arrange
            selectCourse(-1);
            const changeEvent = new Event('change', { bubbles: true, cancelable: false });

            // Act
            selector.dispatchEvent(changeEvent);

            // Assert
            expect(component.activeCourseId).toBeNull();
            expect(component.disabled).toBeTruthy();
        });

        it('should load profile when course is selected', () => {
            // Arrange
            const courseId = 1;
            selectCourse(courseId);
            const changeEvent = new Event('change', { bubbles: true, cancelable: false });

            // Act
            selector.dispatchEvent(changeEvent);

            // Assert
            expect(component.activeCourseId).toBe(courseId);
            expect(component.disabled).toBeFalsy();
            expect(component.aimForGradeOrBonus()).toBe(clp1.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(clp1.timeInvestment);
            expect(component.repetitionIntensity()).toBe(clp1.repetitionIntensity);
        });
    });

    describe('Profile management', () => {
        it('should update profile values correctly through course selection', () => {
            // Arrange
            const profile = new CourseLearnerProfileDTO();
            profile.id = 1;
            profile.courseId = 1; // Set courseId so it can be found
            profile.courseTitle = 'Test Course';
            profile.aimForGradeOrBonus = 3;
            profile.timeInvestment = 4;
            profile.repetitionIntensity = 5;
            component.courseLearnerProfiles.set([profile]);

            // Act - Test through course selection which internally calls updateProfileValues
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert
            expect(component.aimForGradeOrBonus()).toBe(3);
            expect(component.timeInvestment()).toBe(4);
            expect(component.repetitionIntensity()).toBe(5);
        });

        it('should get course learner profile by course ID through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection which internally calls getCourseLearnerProfile
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should load the profile for course 1
            expect(component.aimForGradeOrBonus()).toBe(clp1.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(clp1.timeInvestment);
            expect(component.repetitionIntensity()).toBe(clp1.repetitionIntensity);
        });

        it('should return undefined for non-existent course ID through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection with non-existent course
            const mockEvent = {
                target: {
                    value: '999',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should remain at default values since no profile was found
            expect(component.aimForGradeOrBonus()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.timeInvestment()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.repetitionIntensity()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
        });

        it('should load profile for course through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection which internally calls loadProfileForCourse
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should load the profile for course 1
            expect(component.aimForGradeOrBonus()).toBe(clp1.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(clp1.timeInvestment);
            expect(component.repetitionIntensity()).toBe(clp1.repetitionIntensity);
        });

        it('should not load profile for non-existent course through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection with non-existent course
            const mockEvent = {
                target: {
                    value: '999',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should remain at default values since no profile was found
            expect(component.aimForGradeOrBonus()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.timeInvestment()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.repetitionIntensity()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
        });

        it('should load profiles successfully through ngOnInit', async () => {
            // Arrange
            const profiles = [clp1, clp2];
            jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockResolvedValue(profiles);

            // Act - Call ngOnInit which internally calls loadProfiles
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.courseLearnerProfiles()).toEqual(profiles);
        });

        it('should handle error when loading profiles fails through ngOnInit', async () => {
            // Arrange
            const error = new Error('Failed to load profiles');
            jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockRejectedValue(error);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which internally calls loadProfiles
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should validate profile values before update', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Set invalid values
            component.aimForGradeOrBonus.set(-1);
            component.timeInvestment.set(6);
            component.repetitionIntensity.set(7);

            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.courseLearnerProfile.invalidRange',
            });
        });

        it('should update profile values in component state after successful update', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            const newProfile = new CourseLearnerProfileDTO();
            Object.assign(newProfile, Object.assign({}, profiles[courseIndex]));
            newProfile.aimForGradeOrBonus = 3;
            newProfile.timeInvestment = 3;
            newProfile.repetitionIntensity = 3;

            component.aimForGradeOrBonus.set(newProfile.aimForGradeOrBonus);
            component.timeInvestment.set(newProfile.timeInvestment);
            component.repetitionIntensity.set(newProfile.repetitionIntensity);

            putUpdatedCourseLearnerProfileSpy.mockResolvedValue(newProfile);

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(component.aimForGradeOrBonus()).toBe(newProfile.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(newProfile.timeInvestment);
            expect(component.repetitionIntensity()).toBe(newProfile.repetitionIntensity);
        });

        it('should test updateProfileValues method through course selection', () => {
            // Arrange
            const testProfile = new CourseLearnerProfileDTO();
            testProfile.id = 1;
            testProfile.courseId = 1; // Set courseId so it can be found
            testProfile.courseTitle = 'Test Course';
            testProfile.aimForGradeOrBonus = 3;
            testProfile.timeInvestment = 4;
            testProfile.repetitionIntensity = 5;
            component.courseLearnerProfiles.set([testProfile]);

            // Act - Test through course selection which internally calls updateProfileValues
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert
            expect(component.aimForGradeOrBonus()).toBe(3);
            expect(component.timeInvestment()).toBe(4);
            expect(component.repetitionIntensity()).toBe(5);
        });

        it('should test getCourseLearnerProfile method with existing course through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection which internally calls getCourseLearnerProfile
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should load the profile for course 1
            expect(component.aimForGradeOrBonus()).toBe(clp1.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(clp1.timeInvestment);
            expect(component.repetitionIntensity()).toBe(clp1.repetitionIntensity);
        });

        it('should test getCourseLearnerProfile method with non-existing course through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection with non-existent course
            const mockEvent = {
                target: {
                    value: '999',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should remain at default values since no profile was found
            expect(component.aimForGradeOrBonus()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.timeInvestment()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.repetitionIntensity()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
        });

        it('should test loadProfileForCourse method with existing course through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection which internally calls loadProfileForCourse
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should load the profile for course 1
            expect(component.aimForGradeOrBonus()).toBe(clp1.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(clp1.timeInvestment);
            expect(component.repetitionIntensity()).toBe(clp1.repetitionIntensity);
        });

        it('should test loadProfileForCourse method with non-existing course through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set(profiles);

            // Act - Test through course selection with non-existent course
            const mockEvent = {
                target: {
                    value: '999',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should remain at default values since no profile was found
            expect(component.aimForGradeOrBonus()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.timeInvestment()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
            expect(component.repetitionIntensity()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
        });

        it('should test loadProfiles method through ngOnInit', async () => {
            // Arrange
            const testProfiles = [clp1, clp2];
            jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockResolvedValue(testProfiles);

            // Act - Call ngOnInit which internally calls loadProfiles
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.courseLearnerProfiles()).toEqual(testProfiles);
        });

        it('should test loadProfiles method with error handling through ngOnInit', async () => {
            // Arrange
            const error = new Error('Load failed');
            jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockRejectedValue(error);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which internally calls loadProfiles
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should test courseChanged method with valid course selection', () => {
            // Arrange
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseLearnerProfiles.set(profiles);

            // Act
            component.courseChanged(mockEvent);

            // Assert
            expect(component.activeCourseId).toBe(1);
            expect(component.disabled).toBeFalsy();
            expect(component.aimForGradeOrBonus()).toBe(clp1.aimForGradeOrBonus);
            expect(component.timeInvestment()).toBe(clp1.timeInvestment);
            expect(component.repetitionIntensity()).toBe(clp1.repetitionIntensity);
        });

        it('should test courseChanged method with no course selection', () => {
            // Arrange
            const mockEvent = {
                target: {
                    value: '-1',
                },
            } as unknown as Event;

            // Act
            component.courseChanged(mockEvent);

            // Assert
            expect(component.activeCourseId).toBeNull();
            expect(component.disabled).toBeTruthy();
        });
    });

    describe('Error handling and edge cases', () => {
        it('should test handleError method with HttpErrorResponse and title through API failure', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                error: { title: 'Custom error message' },
                status: 400,
            });
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Custom error message',
                disableTranslation: true,
            });
        });

        it('should test handleError method with HttpErrorResponse and header through API failure', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                headers: new HttpHeaders().set('x-artemisapp-alert', 'Header error message'),
                status: 400,
            });
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Header error message',
                disableTranslation: true,
            });
        });

        it('should test handleError method with HttpErrorResponse without title or header through API failure', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                status: 500,
            });
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(httpError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An error occurred',
                disableTranslation: true,
            });
        });

        it('should test handleError method with generic error through API failure', async () => {
            // Arrange
            const genericError = new Error('Generic error');
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(genericError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should test handleError method with null error through API failure', async () => {
            // Arrange
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(null);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should test handleError method with string error through API failure', async () => {
            // Arrange
            const stringError = 'String error';
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(stringError);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Set up component state
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Act - Call onToggleChange which will trigger handleError
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should test loadProfiles method with error handling through ngOnInit', async () => {
            // Arrange
            const error = new Error('Load failed');
            jest.spyOn(learnerProfileApiService, 'getCourseLearnerProfilesForCurrentUser').mockRejectedValue(error);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which internally calls loadProfiles
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should test onToggleChange with invalid profile values', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            // Set invalid values
            component.aimForGradeOrBonus.set(-1);
            component.timeInvestment.set(6);
            component.repetitionIntensity.set(7);

            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.courseLearnerProfile.invalidRange',
            });
        });

        it('should test onToggleChange with success and alert handling', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            const newProfile = new CourseLearnerProfileDTO();
            Object.assign(newProfile, Object.assign({}, profiles[courseIndex]));
            newProfile.aimForGradeOrBonus = 3;
            newProfile.timeInvestment = 3;
            newProfile.repetitionIntensity = 3;

            component.aimForGradeOrBonus.set(newProfile.aimForGradeOrBonus);
            component.timeInvestment.set(newProfile.timeInvestment);
            component.repetitionIntensity.set(newProfile.repetitionIntensity);

            putUpdatedCourseLearnerProfileSpy.mockResolvedValue(newProfile);
            const closeAllSpy = jest.spyOn(alertService, 'closeAll');
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
            expect(closeAllSpy).toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.courseLearnerProfile.profileSaved',
            });
        });

        it('should test onToggleChange with error during save', async () => {
            // Arrange
            const courseIndex = 1;
            const courseId = profiles[courseIndex].courseId;
            component.courseLearnerProfiles.set([...profiles]);
            component.activeCourseId = courseId;
            component.disabled = false;

            const error = new Error('Save failed');
            putUpdatedCourseLearnerProfileSpy.mockRejectedValue(error);
            const alertSpy = jest.spyOn(alertService, 'addAlert');

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'An unexpected error occurred',
                disableTranslation: true,
            });
        });

        it('should test component with empty profiles array through course selection', () => {
            // Arrange
            component.courseLearnerProfiles.set([]);

            // Act - Test through course selection
            const mockEvent = {
                target: {
                    value: '1',
                },
            } as unknown as Event;
            component.courseChanged(mockEvent);

            // Assert - Should not find profile and remain disabled
            expect(component.activeCourseId).toBe(1);
            expect(component.disabled).toBeFalsy();
            // The profile values should remain at default since no profile was found
            expect(component.aimForGradeOrBonus()).toBe(CourseLearnerProfileDTO.MIN_VALUE);
        });

        it('should test component with null activeCourseId', async () => {
            // Arrange
            component.activeCourseId = null;
            component.disabled = false;

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
        });

        it('should test component with undefined activeCourseId', async () => {
            // Arrange
            component.activeCourseId = undefined as any;
            component.disabled = false;

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putUpdatedCourseLearnerProfileSpy).not.toHaveBeenCalled();
        });

        it('should test component signal updates', () => {
            // Arrange & Act
            component.aimForGradeOrBonus.set(1);
            component.timeInvestment.set(2);
            component.repetitionIntensity.set(3);

            // Assert
            expect(component.aimForGradeOrBonus()).toBe(1);
            expect(component.timeInvestment()).toBe(2);
            expect(component.repetitionIntensity()).toBe(3);
        });

        it('should test component disabled state changes', () => {
            // Arrange & Act
            component.disabled = false;
            expect(component.disabled).toBeFalsy();

            component.disabled = true;
            expect(component.disabled).toBeTruthy();
        });

        it('should test component with courseId as string', () => {
            // Arrange
            const mockEvent = {
                target: {
                    value: '1', // String value
                },
            } as unknown as Event;

            // Act
            component.courseChanged(mockEvent);

            // Assert
            expect(component.activeCourseId).toBe(1); // Should be converted to number
            expect(component.disabled).toBeFalsy();
        });

        it('should test component with invalid courseId string', () => {
            // Arrange
            const mockEvent = {
                target: {
                    value: 'invalid',
                },
            } as unknown as Event;

            // Act
            component.courseChanged(mockEvent);

            // Assert
            expect(component.activeCourseId).toBeNaN();
            expect(component.disabled).toBeFalsy();
        });
    });
});
