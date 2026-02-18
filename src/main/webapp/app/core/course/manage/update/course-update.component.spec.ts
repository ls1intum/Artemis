import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { LoadedImage } from 'app/shared/image-cropper/interfaces/loaded-image.interface';
import { LoadImageService } from 'app/shared/image-cropper/services/load-image.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseUpdateComponent } from 'app/core/course/manage/update/course-update.component';
import { Course, CourseInformationSharingConfiguration, isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ImageCropperComponent } from 'app/shared/image-cropper/component/image-cropper.component';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';
import dayjs from 'dayjs/esm';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { By } from '@angular/platform-browser';
import { EventManager } from 'app/shared/service/event-manager.service';
import { cloneDeep } from 'lodash-es';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ImageCropperModalComponent } from 'app/core/course/manage/image-cropper-modal/image-cropper-modal.component';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_CAMPUS_ONLINE, MODULE_FEATURE_LTI } from 'app/app.constants';
import { CampusOnlineCourseDTO, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';
import { CampusOnlineConfiguration } from 'app/core/course/shared/entities/campus-online-configuration.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { FileService } from 'app/shared/service/file.service';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';

describe('Course Management Update Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseUpdateComponent;
    let fixture: ComponentFixture<CourseUpdateComponent>;
    let courseManagementService: CourseManagementService;
    let courseAdminService: CourseAdminService;
    let profileService: ProfileService;
    let organizationService: OrganizationManagementService;
    let loadImageService: LoadImageService;
    let accountService: AccountService;
    let course: Course;
    const validTimeZone = 'Europe/Berlin';
    let loadImageSpy: ReturnType<typeof vi.spyOn>;
    let eventManager: EventManager;
    let dialogService: DialogService;

    beforeEach(async () => {
        course = new Course();
        course.id = 123;
        course.title = 'testCourseTitle';
        course.shortName = 'testShortName';
        course.description = 'description';
        course.startDate = dayjs();
        course.endDate = dayjs();
        course.semester = 'testSemester';
        course.defaultProgrammingLanguage = ProgrammingLanguage.PYTHON;
        course.testCourse = true;
        course.onlineCourse = true;
        course.complaintsEnabled = true;
        course.requestMoreFeedbackEnabled = true;
        course.maxComplaints = 12;
        course.maxTeamComplaints = 13;
        course.maxComplaintTimeDays = 14;
        course.maxComplaintTextLimit = 500;
        course.maxComplaintResponseTextLimit = 1000;
        course.maxRequestMoreFeedbackTimeDays = 15;
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        course.enrollmentEnabled = true;
        course.enrollmentConfirmationMessage = 'testEnrollmentConfirmationMessage';
        course.presentationScore = 16;
        course.color = 'testColor';
        course.courseIcon = 'testCourseIcon';
        course.courseIconPath = 'api/core/files/testCourseIcon';
        course.timeZone = 'Europe/London';
        course.learningPathsEnabled = true;
        course.studentCourseAnalyticsDashboardEnabled = false;

        const route = {
            data: of({ course }),
        } as any as ActivatedRoute;

        (Intl as any).supportedValuesOf = () => [validTimeZone];

        await TestBed.configureTestingModule({
            imports: [CourseUpdateComponent, ReactiveFormsModule, FormsModule, ImageCropperComponent, NgbTooltipModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(DialogService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: Router, useClass: MockRouter },
                MockProvider(LoadImageService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseUpdateComponent);
        comp = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        courseAdminService = TestBed.inject(CourseAdminService);
        profileService = TestBed.inject(ProfileService);
        organizationService = TestBed.inject(OrganizationManagementService);
        loadImageService = TestBed.inject(LoadImageService);
        loadImageSpy = vi.spyOn(loadImageService, 'loadImageFile');
        accountService = TestBed.inject(AccountService);
        eventManager = TestBed.inject(EventManager);
        dialogService = TestBed.inject(DialogService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    describe('ngOnInit', () => {
        it('should get course, profile and fill the form', async () => {
            const profileInfo = { activeProfiles: [], activeModuleFeatures: [MODULE_FEATURE_ATLAS, MODULE_FEATURE_LTI] } as unknown as ProfileInfo;
            const getProfileStub = vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
            const organization = new Organization();
            organization.id = 12344;
            const getOrganizationsStub = vi.spyOn(organizationService, 'getOrganizationsByCourse').mockReturnValue(of([organization]));

            comp.ngOnInit();
            fixture.detectChanges();
            await Promise.resolve();
            expect(comp.course).toEqual(course);
            expect(comp.courseOrganizations).toEqual([organization]);
            expect(getOrganizationsStub).toHaveBeenCalled();
            expect(getOrganizationsStub).toHaveBeenCalledWith(course.id);
            expect(getProfileStub).toHaveBeenCalled();
            expect(comp.customizeGroupNames).toBe(true);
            expect(comp.course.studentGroupName).toBe('artemis-dev');
            expect(comp.course.teachingAssistantGroupName).toBe('artemis-dev');
            expect(comp.course.editorGroupName).toBe('artemis-dev');
            expect(comp.course.instructorGroupName).toBe('artemis-dev');
            expect(comp.courseForm.get(['id'])?.value).toBe(course.id);
            expect(comp.courseForm.get(['title'])?.value).toBe(course.title);
            expect(comp.shortName.value).toBe(course.shortName);
            expect(comp.courseForm.get(['studentGroupName'])?.value).toBe(course.studentGroupName);
            expect(comp.courseForm.get(['teachingAssistantGroupName'])?.value).toBe(course.teachingAssistantGroupName);
            expect(comp.courseForm.get(['editorGroupName'])?.value).toBe(course.editorGroupName);
            expect(comp.courseForm.get(['instructorGroupName'])?.value).toBe(course.instructorGroupName);
            expect(comp.courseForm.get(['startDate'])?.value).toBe(course.startDate);
            expect(comp.courseForm.get(['endDate'])?.value).toBe(course.endDate);
            expect(comp.courseForm.get(['semester'])?.value).toBe(course.semester);
            expect(comp.courseForm.get(['defaultProgrammingLanguage'])?.value).toBe(course.defaultProgrammingLanguage);
            expect(comp.courseForm.get(['testCourse'])?.value).toBe(course.testCourse);
            expect(comp.courseForm.get(['onlineCourse'])?.value).toBe(course.onlineCourse);
            expect(comp.courseForm.get(['complaintsEnabled'])?.value).toBe(course.complaintsEnabled);
            expect(comp.courseForm.get(['requestMoreFeedbackEnabled'])?.value).toBe(course.requestMoreFeedbackEnabled);
            expect(comp.courseForm.get(['maxComplaints'])?.value).toBe(course.maxComplaints);
            expect(comp.courseForm.get(['maxTeamComplaints'])?.value).toBe(course.maxTeamComplaints);
            expect(comp.courseForm.get(['maxComplaintTimeDays'])?.value).toBe(course.maxComplaintTimeDays);
            expect(comp.courseForm.get(['maxComplaintTextLimit'])?.value).toBe(course.maxComplaintTextLimit);
            expect(comp.courseForm.get(['maxComplaintResponseTextLimit'])?.value).toBe(course.maxComplaintResponseTextLimit);
            expect(comp.courseForm.get(['maxRequestMoreFeedbackTimeDays'])?.value).toBe(course.maxRequestMoreFeedbackTimeDays);
            expect(comp.messagingEnabled).toBe(isMessagingEnabled(course));
            expect(comp.communicationEnabled).toBe(isCommunicationEnabled(course));
            expect(comp.courseForm.get(['enrollmentEnabled'])?.value).toBe(course.enrollmentEnabled);
            expect(comp.courseForm.get(['enrollmentConfirmationMessage'])?.value).toBe(course.enrollmentConfirmationMessage);
            expect(comp.courseForm.get(['color'])?.value).toBe(course.color);
            expect(comp.courseForm.get(['courseIcon'])?.value).toBe(course.courseIcon);
            expect(comp.courseForm.get(['learningPathsEnabled'])?.value).toBe(course.learningPathsEnabled);
            expect(comp.courseForm.get(['studentCourseAnalyticsDashboardEnabled'])?.value).toBe(course.studentCourseAnalyticsDashboardEnabled);
        });
    });

    describe('save', () => {
        it('should call update service on save for existing entity', async () => {
            // GIVEN
            const entity = new Course();
            entity.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            entity.id = 123;
            const updateStub = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                id: new FormControl(entity.id),
                onlineCourse: new FormControl(entity.onlineCourse),
                enrollmentEnabled: new FormControl(entity.enrollmentEnabled),
                restrictedAthenaModulesAccess: new FormControl(entity.restrictedAthenaModulesAccess),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                accuracyOfScores: new FormControl(entity.accuracyOfScores),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                maxComplaintTextLimit: new FormControl(entity.maxComplaintTextLimit),
                maxComplaintResponseTextLimit: new FormControl(entity.maxComplaintResponseTextLimit),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                requestMoreFeedbackEnabled: new FormControl(entity.requestMoreFeedbackEnabled),
                maxRequestMoreFeedbackTimeDays: new FormControl(entity.maxRequestMoreFeedbackTimeDays),
                isAtLeastTutor: new FormControl(entity.isAtLeastTutor),
                isAtLeastEditor: new FormControl(entity.isAtLeastEditor),
                isAtLeastInstructor: new FormControl(entity.isAtLeastInstructor),
            });
            // WHEN
            comp.save();
            fixture.detectChanges();
            await Promise.resolve();

            // THEN
            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(entity.id, entity, undefined);
            expect(comp.isSaving).toBe(false);
        });

        it('should call create service on save for new entity', async () => {
            // GIVEN
            const entity = new Course();
            entity.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            const createStub = vi.spyOn(courseAdminService, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(entity.onlineCourse),
                enrollmentEnabled: new FormControl(entity.enrollmentEnabled),
                restrictedAthenaModulesAccess: new FormControl(entity.restrictedAthenaModulesAccess),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                accuracyOfScores: new FormControl(entity.accuracyOfScores),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                maxComplaintTextLimit: new FormControl(entity.maxComplaintTextLimit),
                maxComplaintResponseTextLimit: new FormControl(entity.maxComplaintResponseTextLimit),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                requestMoreFeedbackEnabled: new FormControl(entity.requestMoreFeedbackEnabled),
                maxRequestMoreFeedbackTimeDays: new FormControl(entity.maxRequestMoreFeedbackTimeDays),
                isAtLeastTutor: new FormControl(entity.isAtLeastTutor),
                isAtLeastEditor: new FormControl(entity.isAtLeastEditor),
                isAtLeastInstructor: new FormControl(entity.isAtLeastInstructor),
            }); // mocking reactive form
            // WHEN
            comp.save();
            fixture.detectChanges();
            await Promise.resolve();

            // THEN
            expect(createStub).toHaveBeenCalledOnce();
            expect(createStub).toHaveBeenCalledWith(entity, undefined);
            expect(comp.isSaving).toBe(false);
        });

        it('should broadcast course modification on delete', async () => {
            // GIVEN
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');

            const previousCourse = new Course();
            previousCourse.id = 123;
            previousCourse.title = 'previous title';
            comp.course = previousCourse;

            const updatedCourse = cloneDeep(previousCourse);
            updatedCourse.title = 'updated title';
            comp.courseForm = new FormGroup({
                title: new FormControl(updatedCourse.title),
            });
            const updateStub = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            // WHEN
            comp.save();
            fixture.detectChanges();
            await Promise.resolve();

            // THEN
            expect(updateStub).toHaveBeenCalledOnce();
            expect(broadcastSpy).toHaveBeenCalledWith({
                name: 'courseModification',
                content: 'Changed a course',
            });
        });
    });

    describe('onSelectedColor', () => {
        it('should update form', () => {
            const selectedColor = 'testSelectedColor';
            comp.ngOnInit();
            comp.onSelectedColor(selectedColor);
            expect(comp.courseForm.get(['color'])?.value).toBe(selectedColor);
        });
    });

    describe('setCourseImage', () => {
        beforeEach(() => {
            const mockDialogRef = {
                onClose: of(undefined),
            } as unknown as DynamicDialogRef;
            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        });

        it('should change course image', () => {
            const file = new File([''], 'testFilename');
            const fileList = {
                0: file,
                length: 1,
                item: () => file,
            } as unknown as FileList;
            const event = { currentTarget: { files: fileList } } as unknown as Event;
            comp.setCourseImage(event);
            expect(comp.courseImageUploadFile).toEqual(file);
        });

        it('should trigger openCropper when a file is selected', async () => {
            const openCropperSpy = vi.spyOn(comp, 'openCropper');
            const file = new File([''], 'test-file.jpg', { type: 'image/jpeg' });
            const mockEvent = {
                target: {
                    files: [file],
                    value: '',
                },
                currentTarget: {
                    files: [file],
                },
            } as any;
            if (comp.setCourseImage) {
                comp.setCourseImage(mockEvent);
            }
            fixture.detectChanges();
            await Promise.resolve();
            expect(openCropperSpy).toHaveBeenCalled();
        });
    });

    describe('changeOnlineCourse', () => {
        it('should disable enrollment enabled if course becomes online', () => {
            comp.course = new Course();
            comp.course.onlineCourse = false;
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(false),
                enrollmentEnabled: new FormControl(true),
            });
            expect(comp.courseForm.controls['enrollmentEnabled'].value).toBe(true);
            expect(comp.courseForm.controls['onlineCourse'].value).toBe(false);
            comp.changeOnlineCourse();
            expect(comp.courseForm.controls['enrollmentEnabled'].value).toBe(false);
            expect(comp.courseForm.controls['onlineCourse'].value).toBe(true);
            expect(comp.course.onlineCourse).toBe(true);
        });
    });

    describe('changeEnrollmentEnabled', () => {
        it('should disable online course if enrollment becomes enabled', () => {
            comp.course = new Course();
            comp.course.enrollmentEnabled = false;
            comp.courseForm = new FormGroup({
                enrollmentEnabled: new FormControl(false),
                onlineCourse: new FormControl(true),
                enrollmentStartDate: new FormControl(),
                enrollmentEndDate: new FormControl(),
            });
            expect(comp.courseForm.controls['enrollmentEnabled'].value).toBe(false);
            expect(comp.courseForm.controls['onlineCourse'].value).toBe(true);
            comp.changeEnrollmentEnabled();
            expect(comp.courseForm.controls['onlineCourse'].value).toBe(false);
            expect(comp.courseForm.controls['enrollmentEnabled'].value).toBe(true);
            expect(comp.course.enrollmentEnabled).toBe(true);
        });

        it('should call unenrollmentEnabled', () => {
            const enabelunrollSpy = vi.spyOn(comp, 'changeUnenrollmentEnabled').mockReturnValue();
            comp.course = new Course();
            comp.course.enrollmentEnabled = true;
            comp.course.unenrollmentEnabled = true;
            comp.courseForm = new FormGroup({
                enrollmentEnabled: new FormControl(false),
                onlineCourse: new FormControl(true),
                enrollmentStartDate: new FormControl(),
                enrollmentEndDate: new FormControl(),
            });
            comp.changeEnrollmentEnabled();
            expect(enabelunrollSpy).toHaveBeenCalledOnce();
        });

        it('should set enrollment start and end date to undefined when enrollment is disabled', () => {
            comp.course = new Course();
            comp.course.enrollmentEnabled = true;
            comp.course.enrollmentStartDate = dayjs();
            comp.course.enrollmentEndDate = dayjs().add(1, 'day');
            comp.courseForm = new FormGroup({
                enrollmentEnabled: new FormControl(true),
                enrollmentStartDate: new FormControl(comp.course.enrollmentStartDate),
                enrollmentEndDate: new FormControl(comp.course.enrollmentEndDate),
            });
            comp.changeEnrollmentEnabled();
            expect(comp.courseForm.controls['enrollmentStartDate'].value).toBeUndefined();
            expect(comp.courseForm.controls['enrollmentEndDate'].value).toBeUndefined();
            expect(comp.course.enrollmentStartDate).toBeUndefined();
            expect(comp.course.enrollmentEndDate).toBeUndefined();
            expect(comp.courseForm.controls['enrollmentEnabled'].value).toBe(false);
        });

        it('should set undefined enrollment start and end date to course start and end date when enrollment is enabled', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs();
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = undefined;
            comp.course.enrollmentEndDate = undefined;
            comp.course.enrollmentEnabled = false;
            const expectedEnrollmentEndDate = comp.course.endDate.subtract(1, 'minute');
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(false),
                enrollmentEnabled: new FormControl(false),
                enrollmentStartDate: new FormControl(),
                enrollmentEndDate: new FormControl(),
            });
            comp.changeEnrollmentEnabled();
            expect(comp.course.enrollmentStartDate).toBe(comp.course.startDate);
            expect(comp.course.enrollmentEndDate).toStrictEqual(expectedEnrollmentEndDate);
            expect(comp.courseForm.controls['enrollmentStartDate'].value).toBe(comp.course.startDate);
            expect(comp.courseForm.controls['enrollmentEndDate'].value).toStrictEqual(expectedEnrollmentEndDate);
            expect(comp.courseForm.controls['enrollmentEnabled'].value).toBe(true);
        });
    });

    describe('updateCourseInformationSharingMessagingCodeOfConduct', () => {
        it('should update course information sharing code of conduct', () => {
            comp.course = new Course();
            comp.courseForm = new FormGroup({
                courseInformationSharingMessagingCodeOfConduct: new FormControl(),
            });
            comp.updateCourseInformationSharingMessagingCodeOfConduct('# Code of Conduct');
            expect(comp.courseForm.controls['courseInformationSharingMessagingCodeOfConduct'].value).toBe('# Code of Conduct');
        });

        it('should update course information sharing code of conduct when communication is enabled and messaging disabled', () => {
            comp.communicationEnabled = true;
            comp.messagingEnabled = false;
            comp.course = new Course();
            comp.courseForm = new FormGroup({
                courseInformationSharingMessagingCodeOfConduct: new FormControl(),
            });
            comp.updateCourseInformationSharingMessagingCodeOfConduct('# Code of Conduct');
            expect(comp.courseForm.controls['courseInformationSharingMessagingCodeOfConduct'].value).toBe('# Code of Conduct');
            // Verify the form control is editable
            expect(comp.courseForm.controls['courseInformationSharingMessagingCodeOfConduct'].enabled).toBe(true);
        });
    });

    describe('changeComplaintsEnabled', () => {
        it('should initialize values if enabled and reset if disabled', () => {
            comp.courseForm = new FormGroup({
                maxComplaints: new FormControl(2),
                maxTeamComplaints: new FormControl(2),
                maxComplaintTimeDays: new FormControl(2),
                maxComplaintTextLimit: new FormControl(2),
                maxComplaintResponseTextLimit: new FormControl(2),
            });
            comp.complaintsEnabled = false;
            comp.changeComplaintsEnabled();
            expect(comp.courseForm.controls['maxComplaints'].value).toBe(3);
            expect(comp.courseForm.controls['maxTeamComplaints'].value).toBe(3);
            expect(comp.courseForm.controls['maxComplaintTimeDays'].value).toBe(7);
            expect(comp.courseForm.controls['maxComplaintTextLimit'].value).toBe(2000);
            expect(comp.courseForm.controls['maxComplaintResponseTextLimit'].value).toBe(2000);
            expect(comp.complaintsEnabled).toBe(true);
            comp.changeComplaintsEnabled();
            expect(comp.courseForm.controls['maxComplaints'].value).toBe(0);
            expect(comp.courseForm.controls['maxTeamComplaints'].value).toBe(0);
            expect(comp.courseForm.controls['maxComplaintTimeDays'].value).toBe(0);
            expect(comp.courseForm.controls['maxComplaintTextLimit'].value).toBe(2000);
            expect(comp.courseForm.controls['maxComplaintResponseTextLimit'].value).toBe(2000);
            expect(comp.complaintsEnabled).toBe(false);
        });
    });

    describe('changeRequestMoreFeedbackEnabled', () => {
        it('should initialize value if enabled and reset if disabled', () => {
            comp.courseForm = new FormGroup({
                maxRequestMoreFeedbackTimeDays: new FormControl(2),
            });
            comp.requestMoreFeedbackEnabled = false;
            comp.changeRequestMoreFeedbackEnabled();
            expect(comp.courseForm.controls['maxRequestMoreFeedbackTimeDays'].value).toBe(7);
            expect(comp.requestMoreFeedbackEnabled).toBe(true);
            comp.changeRequestMoreFeedbackEnabled();
            expect(comp.courseForm.controls['maxRequestMoreFeedbackTimeDays'].value).toBe(0);
            expect(comp.requestMoreFeedbackEnabled).toBe(false);
        });
    });

    describe('changeUnenrollmentEnabled', () => {
        it('should toggle unenrollment enabled', () => {
            comp.course = new Course();
            comp.course.endDate = dayjs();
            comp.courseForm = new FormGroup({
                unenrollmentEnabled: new FormControl(false),
                unenrollmentEndDate: new FormControl(undefined),
            });
            comp.changeUnenrollmentEnabled();
            expect(comp.courseForm.controls['unenrollmentEnabled'].value).toBeTruthy();
            expect(comp.course.unenrollmentEndDate).toBe(comp.course.endDate);
        });
        it('should toggle unenrollment disabled', () => {
            comp.course = new Course();
            comp.course.endDate = dayjs();
            comp.course.unenrollmentEnabled = true;
            comp.course.unenrollmentEndDate = comp.course.endDate;
            comp.courseForm = new FormGroup({
                unenrollmentEnabled: new FormControl(true),
                unenrollmentEndDate: new FormControl(comp.course.endDate),
            });
            comp.changeUnenrollmentEnabled();
            expect(comp.courseForm.controls['unenrollmentEnabled'].value).toBeFalsy();
            expect(comp.course.unenrollmentEndDate).toBeUndefined();
        });
    });

    describe('changeCustomizeGroupNames', () => {
        it('should initialize values if enabled and reset if disabled', () => {
            comp.course = new Course();
            comp.courseForm = new FormGroup({
                studentGroupName: new FormControl('noname'),
                teachingAssistantGroupName: new FormControl('noname'),
                editorGroupName: new FormControl('noname'),
                instructorGroupName: new FormControl('noname'),
            });
            comp.customizeGroupNames = false;
            comp.changeCustomizeGroupNames();
            expect(comp.courseForm.controls['studentGroupName'].value).toBe('artemis-dev');
            expect(comp.courseForm.controls['teachingAssistantGroupName'].value).toBe('artemis-dev');
            expect(comp.courseForm.controls['editorGroupName'].value).toBe('artemis-dev');
            expect(comp.courseForm.controls['instructorGroupName'].value).toBe('artemis-dev');
            expect(comp.customizeGroupNames).toBe(true);
            comp.changeCustomizeGroupNames();
            expect(comp.courseForm.controls['studentGroupName'].value).toBeUndefined();
            expect(comp.courseForm.controls['teachingAssistantGroupName'].value).toBeUndefined();
            expect(comp.courseForm.controls['editorGroupName'].value).toBeUndefined();
            expect(comp.courseForm.controls['instructorGroupName'].value).toBeUndefined();
            expect(comp.customizeGroupNames).toBe(false);
        });
    });

    describe('changeTestCourseEnabled', () => {
        it('should toggle test course', () => {
            comp.course = new Course();
            comp.course.testCourse = true;
            expect(comp.course.testCourse).toBe(true);
            comp.changeTestCourseEnabled();
            expect(comp.course.testCourse).toBe(false);
            comp.changeTestCourseEnabled();
            expect(comp.course.testCourse).toBe(true);
        });
    });

    describe('changeRestrictedAthenaModulesEnabled', () => {
        it('should toggle restricted athena modules access', () => {
            comp.course = new Course();
            comp.course.restrictedAthenaModulesAccess = true;
            comp.courseForm = new FormGroup({ restrictedAthenaModulesAccess: new FormControl(true) });

            expect(comp.course.restrictedAthenaModulesAccess).toBe(true);
            expect(comp.courseForm.controls['restrictedAthenaModulesAccess'].value).toBeTruthy();
            comp.changeRestrictedAthenaModulesEnabled();
            expect(comp.course.restrictedAthenaModulesAccess).toBe(false);
            expect(comp.courseForm.controls['restrictedAthenaModulesAccess'].value).toBeFalsy();
            comp.changeRestrictedAthenaModulesEnabled();
            expect(comp.course.restrictedAthenaModulesAccess).toBe(true);
            expect(comp.courseForm.controls['restrictedAthenaModulesAccess'].value).toBeTruthy();
        });
    });

    describe('isValidDate', () => {
        it('should handle valid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            expect(comp.isValidDate).toBe(true);
        });

        it('should handle invalid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().add(1, 'day');
            comp.course.endDate = dayjs().subtract(1, 'day');
            expect(comp.isValidDate).toBe(false);
        });
    });

    describe('isValidEnrollmentPeriod', () => {
        it('should handle valid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBe(true);
        });

        it('should not be valid if course start and end date are not set', () => {
            comp.course = new Course();
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBe(false);
        });

        it('should not be valid if course start date is not set', () => {
            comp.course = new Course();
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBe(false);
        });

        it('should not be valid if course end date is not set', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBe(false);
        });

        it('should not be valid if course start and end date are not valid', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().add(1, 'day');
            comp.course.endDate = dayjs().subtract(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBe(false);
        });

        it('should handle invalid enrollment end date before enrollment start date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs().subtract(3, 'day');
            expect(comp.isValidEnrollmentPeriod).toBe(false);
        });

        it('should handle valid enrollment start date after course start date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(2, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(1, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBe(true);
        });

        it('should handle invalid enrollment end date after course end date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs().add(2, 'day');
            expect(comp.isValidEnrollmentPeriod).toBe(false);
        });
    });

    describe('isValidUnenrollmentEndDate', () => {
        it('should handle valid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(true);
        });

        it('should not be valid if enrollment start and end date are not set', () => {
            comp.course = new Course();
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
        });

        it('should not be valid if enrollment start date is not set', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
        });

        it('should not be valid if enrollment end date is not set', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
        });

        it('should not be valid if enrollemnt start and end date are not valid', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs();
            comp.course.enrollmentEndDate = dayjs().subtract(2, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
        });

        it('should handle invalid unenrollment end date before enrollment end date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs().add(1, 'day');
            comp.course.unenrollmentEndDate = dayjs();
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
        });

        it('should handle invalid unenrollment end date after course end date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            comp.course.unenrollmentEndDate = dayjs().add(2, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
        });
    });

    describe('form date control syncing', () => {
        beforeEach(() => {
            comp.ngOnInit();
        });

        it('should sync all date form control changes back to course model', () => {
            const newStartDate = dayjs().subtract(10, 'day');
            const newEndDate = dayjs().add(10, 'day');
            const newEnrollmentStart = dayjs().subtract(5, 'day');
            const newEnrollmentEnd = dayjs().add(3, 'day');
            const newUnenrollmentEnd = dayjs().add(5, 'day');

            // Verify initial state: course model does not yet have these values
            expect(comp.course.startDate).not.toBe(newStartDate);
            expect(comp.course.endDate).not.toBe(newEndDate);
            expect(comp.course.enrollmentStartDate).not.toBe(newEnrollmentStart);
            expect(comp.course.enrollmentEndDate).not.toBe(newEnrollmentEnd);
            expect(comp.course.unenrollmentEndDate).not.toBe(newUnenrollmentEnd);

            // Set each date via form control and verify it syncs to course model
            comp.courseForm.controls['startDate'].setValue(newStartDate);
            expect(comp.course.startDate).toBe(newStartDate);

            comp.courseForm.controls['endDate'].setValue(newEndDate);
            expect(comp.course.endDate).toBe(newEndDate);

            comp.courseForm.controls['enrollmentStartDate'].setValue(newEnrollmentStart);
            expect(comp.course.enrollmentStartDate).toBe(newEnrollmentStart);

            comp.courseForm.controls['enrollmentEndDate'].setValue(newEnrollmentEnd);
            expect(comp.course.enrollmentEndDate).toBe(newEnrollmentEnd);

            comp.courseForm.controls['unenrollmentEndDate'].setValue(newUnenrollmentEnd);
            expect(comp.course.unenrollmentEndDate).toBe(newUnenrollmentEnd);

            // Verify all values are still correct after all updates
            expect(comp.course.startDate).toBe(newStartDate);
            expect(comp.course.endDate).toBe(newEndDate);
            expect(comp.course.enrollmentStartDate).toBe(newEnrollmentStart);
            expect(comp.course.enrollmentEndDate).toBe(newEnrollmentEnd);
            expect(comp.course.unenrollmentEndDate).toBe(newUnenrollmentEnd);
        });

        it('should sync undefined/null values back to course model when dates are cleared', () => {
            // Set initial dates on course model
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(4, 'day');

            // Clear each date via form control and verify it syncs undefined to course model
            comp.courseForm.controls['startDate'].setValue(undefined);
            expect(comp.course.startDate).toBeUndefined();

            comp.courseForm.controls['endDate'].setValue(undefined);
            expect(comp.course.endDate).toBeUndefined();

            comp.courseForm.controls['enrollmentStartDate'].setValue(null);
            expect(comp.course.enrollmentStartDate).toBeNull();

            comp.courseForm.controls['enrollmentEndDate'].setValue(undefined);
            expect(comp.course.enrollmentEndDate).toBeUndefined();

            comp.courseForm.controls['unenrollmentEndDate'].setValue(null);
            expect(comp.course.unenrollmentEndDate).toBeNull();
        });

        it('should keep form control and course model values consistent after multiple updates', () => {
            const date1 = dayjs().add(5, 'day');
            const date2 = dayjs().add(10, 'day');
            const date3 = dayjs().add(15, 'day');

            comp.courseForm.controls['endDate'].setValue(date1);
            expect(comp.course.endDate).toBe(date1);
            expect(comp.courseForm.controls['endDate'].value).toBe(date1);

            comp.courseForm.controls['endDate'].setValue(date2);
            expect(comp.course.endDate).toBe(date2);
            expect(comp.courseForm.controls['endDate'].value).toBe(date2);

            comp.courseForm.controls['endDate'].setValue(date3);
            expect(comp.course.endDate).toBe(date3);
            expect(comp.courseForm.controls['endDate'].value).toBe(date3);
        });

        it('should update isValidDate when startDate or endDate changes via form control', () => {
            // Set valid start/end dates directly on the model first
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            expect(comp.isValidDate).toBe(true);

            // Move startDate after endDate via form control -> should become invalid
            const invalidStartDate = dayjs().add(10, 'day');
            comp.courseForm.controls['startDate'].setValue(invalidStartDate);
            expect(comp.course.startDate).toBe(invalidStartDate);
            expect(comp.isValidDate).toBe(false);

            // Fix by moving endDate further out via form control -> should become valid again
            const laterEndDate = dayjs().add(20, 'day');
            comp.courseForm.controls['endDate'].setValue(laterEndDate);
            expect(comp.course.endDate).toBe(laterEndDate);
            expect(comp.isValidDate).toBe(true);
        });

        it('should update isValidDate to true when endDate is cleared via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            expect(comp.isValidDate).toBe(true);

            // Clearing endDate: atLeastOneDateNotExisting() returns true, so isValidDate = true
            comp.courseForm.controls['endDate'].setValue(undefined);
            expect(comp.course.endDate).toBeUndefined();
            expect(comp.isValidDate).toBe(true);
        });

        it('should invalidate enrollment period when endDate is moved before enrollmentEndDate via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            expect(comp.isValidEnrollmentPeriod).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);

            // Move endDate before enrollmentEndDate -> enrollment period should be invalid
            const newEndDate = dayjs().add(1, 'day');
            comp.courseForm.controls['endDate'].setValue(newEndDate);
            expect(comp.course.endDate).toBe(newEndDate);
            expect(comp.isValidEnrollmentPeriod).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);
        });

        it('should keep enrollment period valid when extending endDate past enrollmentEndDate via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            expect(comp.isValidEnrollmentPeriod).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);

            // Extend endDate further out -> enrollment period should remain valid
            const newEndDate = dayjs().add(10, 'day');
            comp.courseForm.controls['endDate'].setValue(newEndDate);
            expect(comp.course.endDate).toBe(newEndDate);
            expect(comp.isValidEnrollmentPeriod).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);
        });

        it('should invalidate enrollment period when enrollmentStartDate is moved after enrollmentEndDate via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            expect(comp.isValidEnrollmentPeriod).toBe(true);

            // Move enrollmentStartDate after enrollmentEndDate -> invalid
            const invalidEnrollmentStart = dayjs().add(4, 'day');
            comp.courseForm.controls['enrollmentStartDate'].setValue(invalidEnrollmentStart);
            expect(comp.course.enrollmentStartDate).toBe(invalidEnrollmentStart);
            expect(comp.isValidEnrollmentPeriod).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);
        });

        it('should invalidate enrollment period when enrollmentEndDate is moved after endDate via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(5, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            expect(comp.isValidEnrollmentPeriod).toBe(true);

            // Move enrollmentEndDate past endDate -> invalid
            const invalidEnrollmentEnd = dayjs().add(10, 'day');
            comp.courseForm.controls['enrollmentEndDate'].setValue(invalidEnrollmentEnd);
            expect(comp.course.enrollmentEndDate).toBe(invalidEnrollmentEnd);
            expect(comp.isValidEnrollmentPeriod).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);
        });

        it('should update isValidUnenrollmentEndDate when unenrollmentEndDate changes via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(10, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(5, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);

            // Move unenrollmentEndDate past endDate -> invalid
            const invalidUnenrollmentEnd = dayjs().add(15, 'day');
            comp.courseForm.controls['unenrollmentEndDate'].setValue(invalidUnenrollmentEnd);
            expect(comp.course.unenrollmentEndDate).toBe(invalidUnenrollmentEnd);
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);
        });

        it('should invalidate unenrollmentEndDate when it is moved before enrollmentEndDate via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(10, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(5, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(true);

            // Move unenrollmentEndDate before enrollmentEndDate -> invalid
            const invalidUnenrollmentEnd = dayjs().add(1, 'day');
            comp.courseForm.controls['unenrollmentEndDate'].setValue(invalidUnenrollmentEnd);
            expect(comp.course.unenrollmentEndDate).toBe(invalidUnenrollmentEnd);
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);
        });

        it('should invalidate unenrollmentEndDate when endDate is shortened past it via form control', () => {
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(10, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(8, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);

            // Shorten endDate so unenrollmentEndDate is now past it -> invalid
            const shortenedEndDate = dayjs().add(6, 'day');
            comp.courseForm.controls['endDate'].setValue(shortenedEndDate);
            expect(comp.course.endDate).toBe(shortenedEndDate);
            expect(comp.isValidUnenrollmentEndDate).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);
        });

        it('should recover full valid configuration after fixing dates via form controls', () => {
            // Start with a fully valid configuration
            comp.course.startDate = dayjs().subtract(5, 'day');
            comp.course.endDate = dayjs().add(10, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(3, 'day');
            comp.course.enrollmentEndDate = dayjs().add(3, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(5, 'day');
            expect(comp.isValidDate).toBe(true);
            expect(comp.isValidEnrollmentPeriod).toBe(true);
            expect(comp.isValidUnenrollmentEndDate).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);

            // Break it: shorten endDate before enrollmentEndDate
            const brokenEndDate = dayjs().add(1, 'day');
            comp.courseForm.controls['endDate'].setValue(brokenEndDate);
            expect(comp.isValidEnrollmentPeriod).toBe(false);
            expect(comp.isValidConfiguration).toBe(false);

            // Fix it: extend endDate back out
            const fixedEndDate = dayjs().add(20, 'day');
            comp.courseForm.controls['endDate'].setValue(fixedEndDate);
            expect(comp.course.endDate).toBe(fixedEndDate);
            expect(comp.isValidDate).toBe(true);
            expect(comp.isValidEnrollmentPeriod).toBe(true);
            expect(comp.isValidUnenrollmentEndDate).toBe(true);
            expect(comp.isValidConfiguration).toBe(true);
        });
    });

    describe('removeOrganizationFromCourse', () => {
        it('should remove organization from component', () => {
            const organization = new Organization();
            organization.id = 123;
            const secondOrganization = new Organization();
            secondOrganization.id = 124;
            comp.courseOrganizations = [organization, secondOrganization];
            comp.removeOrganizationFromCourse(organization);
            expect(comp.courseOrganizations).toEqual([secondOrganization]);
        });
    });

    describe('deleteIcon', () => {
        it('should create the delete button when croppedImage is present', () => {
            comp.croppedImage = 'some-image-url';
            fixture.changeDetectorRef.detectChanges();
            const deleteButton = getDeleteIconButton();
            expect(deleteButton).toBeTruthy();
        });

        it('should remove icon image and delete icon button from component', () => {
            const base64String = Buffer.from('testContent').toString('base64');
            loadImageSpy.mockImplementation(() => Promise.resolve({ transformed: { base64: base64String } } as LoadedImage));
            setIcon();
            let deleteIconButton = getDeleteIconButton();
            deleteIconButton.dispatchEvent(new Event('click'));
            fixture.changeDetectorRef.detectChanges();
            const iconImage = fixture.debugElement.nativeElement.querySelector('jhi-image');
            deleteIconButton = getDeleteIconButton();
            expect(iconImage).toBeNull();
            expect(deleteIconButton).toBeNull();
        });

        it('should not be able to delete icon if icon does not exist', () => {
            const iconImage = fixture.debugElement.nativeElement.querySelector('jhi-image');
            const deleteIconButton = getDeleteIconButton();
            expect(iconImage).toBeNull();
            expect(deleteIconButton).toBeNull();
        });

        function setIcon(): void {
            comp.courseImageUploadFile = new File([''], 'testFilename.png', { type: 'image/png' });
            comp.ngOnInit();
            fixture.detectChanges();
        }

        function getDeleteIconButton() {
            return fixture.debugElement.nativeElement.querySelector('#delete-course-icon');
        }
    });

    describe('editIcon', () => {
        it('should create the edit button when croppedImage is present', () => {
            comp.croppedImage = 'some-image-url';
            fixture.changeDetectorRef.detectChanges();
            const editButton = getEditIconButton();
            expect(editButton).toBeTruthy();
        });

        it('should not be able to edit icon if icon does not exist', () => {
            const iconImage = fixture.debugElement.nativeElement.querySelector('jhi-image');
            const editIconButton = getEditIconButton();
            expect(iconImage).toBeNull();
            expect(editIconButton).toBeNull();
        });

        it('should trigger triggerFileInput when edit button is clicked', () => {
            const triggerFileInputSpy = vi.spyOn(comp, 'triggerFileInput').mockImplementation(() => {});
            fixture.detectChanges();
            const editButton = getEditIconButton();
            editButton.dispatchEvent(new Event('click'));
            fixture.detectChanges();
            expect(triggerFileInputSpy).toHaveBeenCalled();
        });

        function getEditIconButton() {
            return fixture.debugElement.nativeElement.querySelector('#edit-course-icon');
        }
    });

    describe('noImagePlaceholder', () => {
        it('should trigger file input when no-image div is clicked', () => {
            const triggerFileInputSpy = vi.spyOn(comp, 'triggerFileInput').mockImplementation(() => {});
            fixture.detectChanges();
            comp.croppedImage = undefined;
            fixture.changeDetectorRef.detectChanges();
            const noImageDiv = fixture.debugElement.nativeElement.querySelector('#no-image-placeholder');
            noImageDiv.dispatchEvent(new Event('click'));
            fixture.detectChanges();
            expect(triggerFileInputSpy).toHaveBeenCalled();
        });
    });

    describe('openImageCropper', () => {
        it('should open the image cropper modal and update the croppedImage on result', () => {
            const croppedImageResult = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA';
            const mockDialogRef = {
                onClose: of(croppedImageResult),
            } as unknown as DynamicDialogRef;
            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            comp.courseImageUploadFile = new File([''], 'filename.png', { type: 'image/png' });
            comp.openCropper();
            expect(dialogService.open).toHaveBeenCalledWith(ImageCropperModalComponent, expect.any(Object));
            expect(comp.croppedImage).toBe(croppedImageResult);
        });
    });

    describe('changeOrganizations', () => {
        let organization: Organization;
        beforeEach(() => {
            organization = new Organization();
            organization.id = 12345;
            vi.spyOn(organizationService, 'getOrganizationsByCourse').mockReturnValue(of([organization]));
        });

        it('should allow adding / removing organizations if admin', () => {
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            fixture.changeDetectorRef.detectChanges();

            const addButton = fixture.debugElement.query(By.css('#addOrganizationButton'));
            const removeButton = fixture.debugElement.query(By.css('#removeOrganizationButton-' + organization.id));

            expect(addButton).not.toBeNull();
            expect(removeButton).not.toBeNull();
        });

        it('should not allow adding / removing organizations if not admin', () => {
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            fixture.changeDetectorRef.detectChanges();

            const addButton = fixture.debugElement.query(By.css('#addOrganizationButton'));
            const removeButton = fixture.debugElement.query(By.css('#removeOrganizationButton'));

            expect(addButton).toBeNull();
            expect(removeButton).toBeNull();
        });
    });

    it('should open organizations modal', () => {
        const mockDialogRef = {
            onClose: of(new Organization()),
        } as unknown as DynamicDialogRef;
        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        comp.openOrganizationsModal();
        expect(comp.courseOrganizations).toHaveLength(1);
    });

    describe('changeCommunicationEnabled', () => {
        let httpMock: HttpTestingController;
        let fileService: FileService;

        beforeEach(() => {
            httpMock = TestBed.inject(HttpTestingController);
            fileService = TestBed.inject(FileService);
        });

        afterEach(() => {
            httpMock.verify();
        });

        it('should load code of conduct template when communication is enabled and code of conduct is not set', async () => {
            const codeOfConduct = '# Code of Conduct Template';
            vi.spyOn(fileService, 'getTemplateCodeOfConduct').mockReturnValue(of(new HttpResponse({ body: codeOfConduct })));

            comp.communicationEnabled = true;
            comp.course = new Course();
            comp.course.courseInformationSharingMessagingCodeOfConduct = undefined;
            comp.courseForm = new FormGroup({
                courseInformationSharingMessagingCodeOfConduct: new FormControl(),
            });

            await comp.changeCommunicationEnabled();

            expect(comp.course.courseInformationSharingMessagingCodeOfConduct).toBe(codeOfConduct);
            expect(comp.courseForm.controls['courseInformationSharingMessagingCodeOfConduct'].value).toBe(codeOfConduct);
        });

        it('should not load code of conduct template when communication is enabled and code of conduct is already set', async () => {
            const existingCodeOfConduct = '# Existing Code of Conduct';
            const getTemplateSpy = vi.spyOn(fileService, 'getTemplateCodeOfConduct');

            comp.communicationEnabled = true;
            comp.course = new Course();
            comp.course.courseInformationSharingMessagingCodeOfConduct = existingCodeOfConduct;
            comp.courseForm = new FormGroup({
                courseInformationSharingMessagingCodeOfConduct: new FormControl(existingCodeOfConduct),
            });

            await comp.changeCommunicationEnabled();

            expect(getTemplateSpy).not.toHaveBeenCalled();
            expect(comp.course.courseInformationSharingMessagingCodeOfConduct).toBe(existingCodeOfConduct);
        });

        it('should disable messaging when communication is enabled', async () => {
            const disableMessagingSpy = vi.spyOn(comp, 'disableMessaging');

            comp.communicationEnabled = true;
            comp.messagingEnabled = true;
            comp.course = new Course();
            comp.course.courseInformationSharingMessagingCodeOfConduct = '# Code of Conduct';

            await comp.changeCommunicationEnabled();

            expect(disableMessagingSpy).toHaveBeenCalledOnce();
            expect(comp.messagingEnabled).toBeFalsy();
        });

        it('should not disable messaging when communication is disabled', async () => {
            const disableMessagingSpy = vi.spyOn(comp, 'disableMessaging');

            comp.communicationEnabled = false;
            comp.messagingEnabled = true;
            comp.course = new Course();

            await comp.changeCommunicationEnabled();

            expect(disableMessagingSpy).not.toHaveBeenCalled();
        });
    });

    describe('CAMPUSOnline integration', () => {
        let campusOnlineService: CampusOnlineService;

        beforeEach(() => {
            campusOnlineService = TestBed.inject(CampusOnlineService);
        });

        it('should set campusOnlineEnabled from profile service', () => {
            const profileInfo = { activeProfiles: [], activeModuleFeatures: [MODULE_FEATURE_CAMPUS_ONLINE] } as unknown as ProfileInfo;
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === MODULE_FEATURE_CAMPUS_ONLINE);

            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.campusOnlineEnabled).toBe(true);
        });

        it('should set campusOnlineLinked when course has configuration', () => {
            course.campusOnlineConfiguration = new CampusOnlineConfiguration();
            course.campusOnlineConfiguration.campusOnlineCourseId = 'CO-101';

            comp.ngOnInit();
            fixture.detectChanges();

            expect(comp.campusOnlineLinked).toBe(true);
        });

        it('should search campus online courses', () => {
            comp.course = course;
            const mockResults: CampusOnlineCourseDTO[] = [
                {
                    campusOnlineCourseId: 'CO-101',
                    title: 'Introduction to CS',
                    semester: '2025W',
                    alreadyImported: false,
                },
            ];
            const searchSpy = vi.spyOn(campusOnlineService, 'searchCourses').mockReturnValue(of(mockResults));

            comp.searchCampusOnline({ query: 'CS' });

            expect(searchSpy).toHaveBeenCalledWith('CS', course.semester);
            expect(comp.campusOnlineSuggestions()).toEqual(mockResults);
        });

        it('should link campus online course for new course (no id)', () => {
            const newCourse = new Course();
            comp.course = newCourse;

            const selectedCourse: CampusOnlineCourseDTO = {
                campusOnlineCourseId: 'CO-101',
                title: 'CS Course',
                responsibleInstructor: 'Prof. Smith',
                department: 'CS Dept',
                studyProgram: 'Informatik BSc',
                alreadyImported: false,
            };

            comp.onCampusOnlineCourseSelected(selectedCourse);

            expect(comp.course.campusOnlineConfiguration).toBeTruthy();
            expect(comp.course.campusOnlineConfiguration?.campusOnlineCourseId).toBe('CO-101');
            expect(comp.course.campusOnlineConfiguration?.responsibleInstructor).toBe('Prof. Smith');
            expect(comp.campusOnlineLinked).toBe(true);
        });

        it('should link campus online course for existing course via API', () => {
            const existingCourse = new Course();
            existingCourse.id = 42;
            comp.course = existingCourse;

            const returnedDTO: CampusOnlineCourseDTO = {
                campusOnlineCourseId: 'CO-101',
                title: 'CS Course',
                alreadyImported: false,
            };
            const linkSpy = vi.spyOn(campusOnlineService, 'linkCourse').mockReturnValue(of(new HttpResponse({ body: returnedDTO })));

            const selectedCourse: CampusOnlineCourseDTO = {
                campusOnlineCourseId: 'CO-101',
                title: 'CS Course',
                responsibleInstructor: 'Prof. Smith',
                department: 'CS Dept',
                studyProgram: 'Informatik BSc',
                alreadyImported: false,
            };

            comp.onCampusOnlineCourseSelected(selectedCourse);

            expect(linkSpy).toHaveBeenCalledWith(42, {
                campusOnlineCourseId: 'CO-101',
                responsibleInstructor: 'Prof. Smith',
                department: 'CS Dept',
                studyProgram: 'Informatik BSc',
            });
            expect(comp.campusOnlineLinked).toBe(true);
            expect(comp.course.campusOnlineConfiguration?.campusOnlineCourseId).toBe('CO-101');
            expect(comp.course.campusOnlineConfiguration?.responsibleInstructor).toBe('Prof. Smith');
            expect(comp.course.campusOnlineConfiguration?.department).toBe('CS Dept');
        });

        it('should unlink campus online course for existing course via API', () => {
            const existingCourse = new Course();
            existingCourse.id = 42;
            existingCourse.campusOnlineConfiguration = new CampusOnlineConfiguration();
            existingCourse.campusOnlineConfiguration.campusOnlineCourseId = 'CO-101';
            comp.course = existingCourse;
            comp.campusOnlineLinked = true;

            const unlinkSpy = vi.spyOn(campusOnlineService, 'unlinkCourse').mockReturnValue(of(new HttpResponse()));

            comp.unlinkCampusOnline();

            expect(unlinkSpy).toHaveBeenCalledWith(42);
            expect(comp.campusOnlineLinked).toBe(false);
            expect(comp.course.campusOnlineConfiguration).toBeUndefined();
            expect(comp.selectedCampusOnlineCourse).toBeUndefined();
        });

        it('should unlink campus online course for new course without API call', () => {
            const newCourse = new Course();
            newCourse.campusOnlineConfiguration = new CampusOnlineConfiguration();
            newCourse.campusOnlineConfiguration.campusOnlineCourseId = 'CO-101';
            comp.course = newCourse;
            comp.campusOnlineLinked = true;

            const unlinkSpy = vi.spyOn(campusOnlineService, 'unlinkCourse');

            comp.unlinkCampusOnline();

            expect(unlinkSpy).not.toHaveBeenCalled();
            expect(comp.campusOnlineLinked).toBe(false);
            expect(comp.course.campusOnlineConfiguration).toBeUndefined();
        });

        it('should preserve campusOnlineConfiguration on save', async () => {
            const entity = new Course();
            entity.id = 123;
            entity.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            entity.campusOnlineConfiguration = new CampusOnlineConfiguration();
            entity.campusOnlineConfiguration.campusOnlineCourseId = 'CO-101';

            const updateStub = vi.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                id: new FormControl(entity.id),
                onlineCourse: new FormControl(entity.onlineCourse),
                enrollmentEnabled: new FormControl(entity.enrollmentEnabled),
                restrictedAthenaModulesAccess: new FormControl(entity.restrictedAthenaModulesAccess),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                accuracyOfScores: new FormControl(entity.accuracyOfScores),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                maxComplaintTextLimit: new FormControl(entity.maxComplaintTextLimit),
                maxComplaintResponseTextLimit: new FormControl(entity.maxComplaintResponseTextLimit),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                requestMoreFeedbackEnabled: new FormControl(entity.requestMoreFeedbackEnabled),
                maxRequestMoreFeedbackTimeDays: new FormControl(entity.maxRequestMoreFeedbackTimeDays),
                isAtLeastTutor: new FormControl(entity.isAtLeastTutor),
                isAtLeastEditor: new FormControl(entity.isAtLeastEditor),
                isAtLeastInstructor: new FormControl(entity.isAtLeastInstructor),
            });

            comp.save();
            fixture.detectChanges();
            await Promise.resolve();

            expect(updateStub).toHaveBeenCalledOnce();
            const savedCourse = updateStub.mock.calls[0][1] as Course;
            expect(savedCourse.campusOnlineConfiguration).toBeTruthy();
            expect(savedCourse.campusOnlineConfiguration?.campusOnlineCourseId).toBe('CO-101');
        });
    });
});

describe('Course Management Student Course Analytics Dashboard Update', () => {
    setupTestBed({ zoneless: true });

    const validTimeZone = 'Europe/Berlin';
    let fixture: ComponentFixture<CourseUpdateComponent>;
    let accountService: AccountService;
    let featureToggleService: FeatureToggleService;
    let featureToggleSpy: ReturnType<typeof vi.spyOn>;
    let profileService: ProfileService;

    beforeEach(async () => {
        (Intl as any).supportedValuesOf = () => [validTimeZone];

        await TestBed.configureTestingModule({
            imports: [CourseUpdateComponent, ReactiveFormsModule, FormsModule, ImageCropperComponent, NgbTooltipModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(LoadImageService),
                MockProvider(DialogService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseUpdateComponent);
        profileService = TestBed.inject(ProfileService);
        accountService = TestBed.inject(AccountService);
        featureToggleService = TestBed.inject(FeatureToggleService);
        featureToggleSpy = vi.spyOn(featureToggleService, 'getFeatureToggleActive');
    });

    afterEach(() => {
        vi.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    it('should hide the form field for dashboard enable toggle when user is not an admin but the feature is toggled.', () => {
        // Simulate a user who is not an admin
        vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);

        // Simulate a feature toggle that includes only the specified feature toggles
        const featureToggleStub = featureToggleSpy.mockImplementation((feature: string) => {
            if (feature === FeatureToggle.StudentCourseAnalyticsDashboard) {
                return of(true);
            }
            return of(false);
        });

        // Run change detection to update the view
        fixture.changeDetectorRef.detectChanges();

        // Try to find the form field in the DOM
        const formGroups = fixture.debugElement.queryAll(By.directive(FeatureToggleHideDirective));
        const filteredFormGroups = formGroups.filter((element) => !element.nativeElement.classList.contains('d-none'));

        expect(featureToggleStub).toHaveBeenCalled();
        expect(filteredFormGroups).toHaveLength(0);
    });
    it('should hide the form field for dashboard enable toggle when user is an admin but the feature is not toggled', () => {
        // Simulate a user who is an admin
        vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);

        const featureToggleStub = featureToggleSpy.mockImplementation((feature: string) => {
            if (feature === FeatureToggle.StudentCourseAnalyticsDashboard || feature === FeatureToggle.LearningPaths) {
                return of(false);
            }
            return of(true);
        });

        // Run change detection to update the view
        fixture.changeDetectorRef.detectChanges();

        // Try to find the form field in the DOM
        const formGroups = fixture.debugElement.queryAll(By.directive(FeatureToggleHideDirective));
        const filteredFormGroups = formGroups.filter((element) => !element.nativeElement.classList.contains('d-none'));

        expect(featureToggleStub).toHaveBeenCalled();
        expect(filteredFormGroups).toHaveLength(0);
    });
    it('should show the form field for dashboard enable toggle when user is an admin and the feature is toggled', () => {
        // Simulate a user who is an admin
        vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);

        const profileInfo = { activeProfiles: [], activeModuleFeatures: [MODULE_FEATURE_ATLAS, MODULE_FEATURE_LTI] } as unknown as ProfileInfo;
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        const featureToggleStub = featureToggleSpy.mockImplementation((feature: string) => {
            if (feature === FeatureToggle.StudentCourseAnalyticsDashboard || feature === FeatureToggle.LearningPaths) {
                return of(true);
            }
            return of(false);
        });

        // Run change detection to update the view
        fixture.changeDetectorRef.detectChanges();

        // Try to find the form field in the DOM
        const formGroups = fixture.debugElement.queryAll(By.directive(FeatureToggleHideDirective));
        const filteredFormGroups = formGroups.filter((element) => !element.nativeElement.classList.contains('d-none'));

        expect(featureToggleStub).toHaveBeenCalled();
        expect(filteredFormGroups).toHaveLength(2);
    });
});

describe('Course Management Update Component Create', () => {
    setupTestBed({ zoneless: true });

    const validTimeZone = 'Europe/Berlin';
    let component: CourseUpdateComponent;
    let fixture: ComponentFixture<CourseUpdateComponent>;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        (Intl as any).supportedValuesOf = () => [validTimeZone];

        await TestBed.configureTestingModule({
            imports: [CourseUpdateComponent, ReactiveFormsModule, FormsModule, ImageCropperComponent, NgbTooltipModule, OwlDateTimeModule, OwlNativeDateTimeModule],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(LoadImageService),
                MockProvider(DialogService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseUpdateComponent);
        component = fixture.componentInstance;
        httpMock = fixture.debugElement.injector.get(HttpTestingController);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    it('should get code of conduct template if a new course is created', () => {
        fixture.detectChanges();
        const req = httpMock.expectOne({ method: 'GET' });
        const codeOfConduct = 'Code of Conduct';
        req.flush(codeOfConduct);
        expect(component.course.courseInformationSharingMessagingCodeOfConduct).toEqual(codeOfConduct);
    });
});
