import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NgbModal, NgbModalRef, NgbTooltipModule, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LoadedImage } from 'app/shared/image-cropper/interfaces/loaded-image.interface';
import { LoadImageService } from 'app/shared/image-cropper/services/load-image.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { Course, CourseInformationSharingConfiguration, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { BehaviorSubject, of } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../test.module';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/entities/organization.model';
import dayjs from 'dayjs/esm';
import { ImageCropperModule } from 'app/shared/image-cropper/image-cropper.module';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { CourseAdminService } from 'app/course/manage/course-admin.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { By } from '@angular/platform-browser';
import { EventManager } from 'app/core/util/event-manager.service';
import { cloneDeep } from 'lodash-es';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';

@Component({ selector: 'jhi-markdown-editor', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('Course Management Update Component', () => {
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
    let loadImageSpy: jest.SpyInstance;
    let eventManager: EventManager;
    let modalService: NgbModal;

    beforeEach(() => {
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
        course.enrollmentConfirmationMessage = 'testRegistrationConfirmationMessage';
        course.presentationScore = 16;
        course.color = 'testColor';
        course.courseIcon = 'testCourseIcon';
        course.timeZone = 'Europe/London';
        course.learningPathsEnabled = true;

        const parentRoute = {
            data: of({ course }),
        } as any as ActivatedRoute;
        const route = { parent: parentRoute } as any as ActivatedRoute;
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ReactiveFormsModule), MockModule(FormsModule), ImageCropperModule, MockDirective(NgbTypeahead), MockModule(NgbTooltipModule)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(TranslateService),
                MockProvider(LoadImageService),
            ],
            declarations: [
                CourseUpdateComponent,
                MarkdownEditorStubComponent,
                MockComponent(ColorSelectorComponent),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(HelpIconComponent),
                MockComponent(SecuredImageComponent),
                MockDirective(FeatureToggleHideDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveKeysPipe),
            ],
        })
            .compileComponents()
            .then(() => {
                (Intl as any).supportedValuesOf = () => [validTimeZone];
                fixture = TestBed.createComponent(CourseUpdateComponent);
                comp = fixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                courseAdminService = TestBed.inject(CourseAdminService);
                profileService = TestBed.inject(ProfileService);
                organizationService = TestBed.inject(OrganizationManagementService);
                loadImageService = TestBed.inject(LoadImageService);
                loadImageSpy = jest.spyOn(loadImageService, 'loadImageFile');
                accountService = TestBed.inject(AccountService);
                eventManager = TestBed.inject(EventManager);
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        (Intl as any).supportedValuesOf = undefined;
    });

    describe('ngOnInit', () => {
        it('should get course, profile and fill the form', fakeAsync(() => {
            const profileInfo = { inProduction: false, activeProfiles: ['lti'] } as ProfileInfo;
            const profileInfoSubject = new BehaviorSubject<ProfileInfo>(profileInfo).asObservable();
            const getProfileStub = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoSubject);
            const organization = new Organization();
            organization.id = 12344;
            const getOrganizationsStub = jest.spyOn(organizationService, 'getOrganizationsByCourse').mockReturnValue(of([organization]));

            comp.ngOnInit();
            expect(comp.course).toEqual(course);
            expect(comp.courseOrganizations).toEqual([organization]);
            expect(getOrganizationsStub).toHaveBeenCalledOnce();
            expect(getOrganizationsStub).toHaveBeenCalledWith(course.id);
            expect(getProfileStub).toHaveBeenCalledOnce();
            expect(comp.customizeGroupNames).toBeTrue();
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
            expect(comp.courseForm.get(['registrationEnabled'])?.value).toBe(course.enrollmentEnabled);
            expect(comp.courseForm.get(['registrationConfirmationMessage'])?.value).toBe(course.enrollmentConfirmationMessage);
            expect(comp.courseForm.get(['color'])?.value).toBe(course.color);
            expect(comp.courseForm.get(['courseIcon'])?.value).toBe(course.courseIcon);
            expect(comp.courseForm.get(['learningPathsEnabled'])?.value).toBe(course.learningPathsEnabled);
            flush();
        }));
    });

    describe('save', () => {
        it('should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new Course();
            entity.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            entity.id = 123;
            const updateStub = jest.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                id: new FormControl(entity.id),
                onlineCourse: new FormControl(entity.onlineCourse),
                registrationEnabled: new FormControl(entity.enrollmentEnabled),
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
            tick(); // simulate async

            // THEN
            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(entity.id, entity, undefined);
            expect(comp.isSaving).toBeFalse();
        }));

        it('should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new Course();
            entity.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
            const createStub = jest.spyOn(courseAdminService, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(entity.onlineCourse),
                registrationEnabled: new FormControl(entity.enrollmentEnabled),
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
            tick(); // simulate async

            // THEN
            expect(createStub).toHaveBeenCalledOnce();
            expect(createStub).toHaveBeenCalledWith(entity, undefined);
            expect(comp.isSaving).toBeFalse();
        }));

        it('should broadcast course modification on delete', fakeAsync(() => {
            // GIVEN
            const broadcastSpy = jest.spyOn(eventManager, 'broadcast');

            const previousCourse = new Course();
            previousCourse.id = 123;
            previousCourse.title = 'previous title';
            comp.course = previousCourse;

            const updatedCourse = cloneDeep(previousCourse);
            updatedCourse.title = 'updated title';
            comp.courseForm = new FormGroup({
                title: new FormControl(updatedCourse.title),
            });
            const updateStub = jest.spyOn(courseManagementService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedCourse })));

            // WHEN
            comp.save();
            tick();

            // THEN
            expect(updateStub).toHaveBeenCalledOnce();
            expect(broadcastSpy).toHaveBeenCalledWith({
                name: 'courseModification',
                content: 'Changed a course',
            });
        }));
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
            const openCropperSpy = jest.spyOn(comp, 'openCropper');
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
            await fixture.whenStable();
            expect(openCropperSpy).toHaveBeenCalled();
        });
    });

    describe('changeOnlineCourse', () => {
        it('should disable registration enabled if course becomes online', () => {
            comp.course = new Course();
            comp.course.onlineCourse = false;
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(false),
                registrationEnabled: new FormControl(true),
            });
            expect(comp.courseForm.controls['registrationEnabled'].value).toBeTrue();
            expect(comp.courseForm.controls['onlineCourse'].value).toBeFalse();
            comp.changeOnlineCourse();
            expect(comp.courseForm.controls['registrationEnabled'].value).toBeFalse();
            expect(comp.courseForm.controls['onlineCourse'].value).toBeTrue();
            expect(comp.course.onlineCourse).toBeTrue();
        });
    });

    describe('changeRegistrationEnabled', () => {
        it('should disable online course if registration becomes enabled', () => {
            comp.course = new Course();
            comp.course.enrollmentEnabled = false;
            comp.courseForm = new FormGroup({
                registrationEnabled: new FormControl(false),
                onlineCourse: new FormControl(true),
                enrollmentStartDate: new FormControl(),
                enrollmentEndDate: new FormControl(),
            });
            expect(comp.courseForm.controls['registrationEnabled'].value).toBeFalse();
            expect(comp.courseForm.controls['onlineCourse'].value).toBeTrue();
            comp.changeRegistrationEnabled();
            expect(comp.courseForm.controls['onlineCourse'].value).toBeFalse();
            expect(comp.courseForm.controls['registrationEnabled'].value).toBeTrue();
            expect(comp.course.enrollmentEnabled).toBeTrue();
        });

        it('should call unenrollmentEnabled', () => {
            const enabelunrollSpy = jest.spyOn(comp, 'changeUnenrollmentEnabled').mockReturnValue();
            comp.course = new Course();
            comp.course.enrollmentEnabled = true;
            comp.course.unenrollmentEnabled = true;
            comp.courseForm = new FormGroup({
                registrationEnabled: new FormControl(false),
                onlineCourse: new FormControl(true),
                enrollmentStartDate: new FormControl(),
                enrollmentEndDate: new FormControl(),
            });
            comp.changeRegistrationEnabled();
            expect(enabelunrollSpy).toHaveBeenCalledOnce();
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
            expect(comp.complaintsEnabled).toBeTrue();
            comp.changeComplaintsEnabled();
            expect(comp.courseForm.controls['maxComplaints'].value).toBe(0);
            expect(comp.courseForm.controls['maxTeamComplaints'].value).toBe(0);
            expect(comp.courseForm.controls['maxComplaintTimeDays'].value).toBe(0);
            expect(comp.courseForm.controls['maxComplaintTextLimit'].value).toBe(2000);
            expect(comp.courseForm.controls['maxComplaintResponseTextLimit'].value).toBe(2000);
            expect(comp.complaintsEnabled).toBeFalse();
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
            expect(comp.requestMoreFeedbackEnabled).toBeTrue();
            comp.changeRequestMoreFeedbackEnabled();
            expect(comp.courseForm.controls['maxRequestMoreFeedbackTimeDays'].value).toBe(0);
            expect(comp.requestMoreFeedbackEnabled).toBeFalse();
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
    });

    describe('changeCustomizeGroupNames', () => {
        it('should initialize values if enabled and reset if disabled', () => {
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
            expect(comp.customizeGroupNames).toBeTrue();
            comp.changeCustomizeGroupNames();
            expect(comp.courseForm.controls['studentGroupName'].value).toBeUndefined();
            expect(comp.courseForm.controls['teachingAssistantGroupName'].value).toBeUndefined();
            expect(comp.courseForm.controls['editorGroupName'].value).toBeUndefined();
            expect(comp.courseForm.controls['instructorGroupName'].value).toBeUndefined();
            expect(comp.customizeGroupNames).toBeFalse();
        });
    });

    describe('changeTestCourseEnabled', () => {
        it('should toggle test course', () => {
            comp.course = new Course();
            comp.course.testCourse = true;
            expect(comp.course.testCourse).toBeTrue();
            comp.changeTestCourseEnabled();
            expect(comp.course.testCourse).toBeFalse();
            comp.changeTestCourseEnabled();
            expect(comp.course.testCourse).toBeTrue();
        });
    });

    describe('changeRestrictedAthenaModulesEnabled', () => {
        it('should toggle restricted athena modules access', () => {
            comp.course = new Course();
            comp.course.restrictedAthenaModulesAccess = true;
            comp.courseForm = new FormGroup({ restrictedAthenaModulesAccess: new FormControl(true) });

            expect(comp.course.restrictedAthenaModulesAccess).toBeTrue();
            expect(comp.courseForm.controls['restrictedAthenaModulesAccess'].value).toBeTruthy();
            comp.changeRestrictedAthenaModulesEnabled();
            expect(comp.course.restrictedAthenaModulesAccess).toBeFalse();
            expect(comp.courseForm.controls['restrictedAthenaModulesAccess'].value).toBeFalsy();
            comp.changeRestrictedAthenaModulesEnabled();
            expect(comp.course.restrictedAthenaModulesAccess).toBeTrue();
            expect(comp.courseForm.controls['restrictedAthenaModulesAccess'].value).toBeTruthy();
        });
    });

    describe('isValidDate', () => {
        it('should handle valid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            expect(comp.isValidDate).toBeTrue();
        });

        it('should handle invalid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().add(1, 'day');
            comp.course.endDate = dayjs().subtract(1, 'day');
            expect(comp.isValidDate).toBeFalse();
        });
    });

    describe('isValidEnrollmentPeriod', () => {
        it('should handle valid dates', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBeTrue();
        });

        it('should not be valid if course start and end date are not set', () => {
            comp.course = new Course();
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
        });

        it('should not be valid if course start date is not set', () => {
            comp.course = new Course();
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
        });

        it('should not be valid if course end date is not set', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
        });

        it('should not be valid if course start and end date are not valid', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().add(1, 'day');
            comp.course.endDate = dayjs().subtract(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
        });

        it('should handle invalid enrollment end date before enrollment start date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs().subtract(3, 'day');
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
        });

        it('should handle invalid enrollment start date after course start date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(2, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(1, 'day');
            comp.course.enrollmentEndDate = dayjs();
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
        });

        it('should handle invalid enrollment end date after course end date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs().add(2, 'day');
            expect(comp.isValidEnrollmentPeriod).toBeFalse();
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
            expect(comp.isValidUnenrollmentEndDate).toBeTrue();
        });

        it('should not be valid if enrollment start and end date are not set', () => {
            comp.course = new Course();
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBeFalse();
        });

        it('should not be valid if enrollment start date is not set', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBeFalse();
        });

        it('should not be valid if enrollment end date is not set', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBeFalse();
        });

        it('should not be valid if enrollemnt start and end date are not valid', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs();
            comp.course.enrollmentEndDate = dayjs().subtract(2, 'day');
            comp.course.unenrollmentEndDate = dayjs().add(1, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBeFalse();
        });

        it('should handle invalid unenrollment end date before enrollment end date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(2, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs().add(1, 'day');
            comp.course.unenrollmentEndDate = dayjs();
            expect(comp.isValidUnenrollmentEndDate).toBeFalse();
        });

        it('should handle invalid unenrollment end date after course end date', () => {
            comp.course = new Course();
            comp.course.startDate = dayjs().subtract(1, 'day');
            comp.course.endDate = dayjs().add(1, 'day');
            comp.course.enrollmentStartDate = dayjs().subtract(2, 'day');
            comp.course.enrollmentEndDate = dayjs();
            comp.course.unenrollmentEndDate = dayjs().add(2, 'day');
            expect(comp.isValidUnenrollmentEndDate).toBeFalse();
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
            fixture.detectChanges();
            const deleteButton = getDeleteIconButton();
            expect(deleteButton).toBeTruthy();
        });

        it('should remove icon image and delete icon button from component', () => {
            const base64String = Buffer.from('testContent').toString('base64');
            loadImageSpy.mockImplementation(() => Promise.resolve({ transformed: { base64: base64String } } as LoadedImage));
            setIcon();
            let deleteIconButton = getDeleteIconButton();
            deleteIconButton.dispatchEvent(new Event('click'));
            fixture.detectChanges();
            const iconImage = fixture.debugElement.nativeElement.querySelector('jhi-secured-image');
            deleteIconButton = getDeleteIconButton();
            expect(iconImage).toBeNull();
            expect(deleteIconButton).toBeNull();
        });

        it('should not be able to delete icon if icon does not exist', () => {
            const iconImage = fixture.debugElement.nativeElement.querySelector('jhi-secured-image');
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
            fixture.detectChanges();
            const editButton = getEditIconButton();
            expect(editButton).toBeTruthy();
        });

        it('should not be able to edit icon if icon does not exist', () => {
            const iconImage = fixture.debugElement.nativeElement.querySelector('jhi-secured-image');
            const editIconButton = getEditIconButton();
            expect(iconImage).toBeNull();
            expect(editIconButton).toBeNull();
        });

        it('should trigger triggerFileInput when edit button is clicked', () => {
            const triggerFileInputSpy = jest.spyOn(comp, 'triggerFileInput').mockImplementation(() => {});
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
            const triggerFileInputSpy = jest.spyOn(comp, 'triggerFileInput').mockImplementation(() => {});
            fixture.detectChanges();
            comp.croppedImage = undefined;
            fixture.detectChanges();
            const noImageDiv = fixture.debugElement.nativeElement.querySelector('#no-image-placeholder');
            noImageDiv.dispatchEvent(new Event('click'));
            fixture.detectChanges();
            expect(triggerFileInputSpy).toHaveBeenCalled();
        });
    });

    describe('openImageCropper', () => {
        it('should open the image cropper modal and update the croppedImage on result', async () => {
            const mockModalRef: Partial<NgbModalRef> = {
                componentInstance: {},
                result: Promise.resolve('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA'),
                close: jest.fn(),
                dismiss: jest.fn(),
            };
            jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
            comp.courseImageUploadFile = new File([''], 'filename.png', { type: 'image/png' });
            comp.openCropper();
            expect(modalService.open).toHaveBeenCalledWith(ImageCropperModalComponent, expect.any(Object));
            const croppedImage = await mockModalRef.result;
            expect(comp.croppedImage).toBe(croppedImage);
        });
    });

    describe('changeOrganizations', () => {
        beforeEach(() => {
            const organization = new Organization();
            organization.id = 12345;
            jest.spyOn(organizationService, 'getOrganizationsByCourse').mockReturnValue(of([organization]));
        });

        it('should allow adding / removing organizations if admin', () => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            fixture.detectChanges();

            const addButton = fixture.debugElement.query(By.css('#addOrganizationButton'));
            const removeButton = fixture.debugElement.query(By.css('#removeOrganizationButton'));

            expect(addButton).not.toBeNull();
            expect(removeButton).not.toBeNull();
        });

        it('should not allow adding / removing organizations if not admin', () => {
            jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            fixture.detectChanges();

            const addButton = fixture.debugElement.query(By.css('#addOrganizationButton'));
            const removeButton = fixture.debugElement.query(By.css('#removeOrganizationButton'));

            expect(addButton).toBeNull();
            expect(removeButton).toBeNull();
        });
    });

    it('should open organizations modal', () => {
        jest.spyOn(modalService, 'open').mockReturnValue({ closed: of(new Organization()), componentInstance: {} } as NgbModalRef);
        comp.openOrganizationsModal();
        expect(comp.courseOrganizations).toHaveLength(1);
    });
});

describe('Course Management Update Component Create', () => {
    const validTimeZone = 'Europe/Berlin';
    let component: CourseUpdateComponent;
    let fixture: ComponentFixture<CourseUpdateComponent>;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                HttpClientTestingModule,
                MockModule(ReactiveFormsModule),
                MockModule(FormsModule),
                ImageCropperModule,
                MockDirective(NgbTypeahead),
                MockModule(NgbTooltipModule),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(TranslateService),
                MockProvider(LoadImageService),
            ],
            declarations: [
                CourseUpdateComponent,
                MarkdownEditorStubComponent,
                MockComponent(ColorSelectorComponent),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(HelpIconComponent),
                MockComponent(SecuredImageComponent),
                MockDirective(FeatureToggleHideDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveKeysPipe),
            ],
        })
            .compileComponents()
            .then(() => {
                (Intl as any).supportedValuesOf = () => [validTimeZone];
                fixture = TestBed.createComponent(CourseUpdateComponent);
                component = fixture.componentInstance;
                httpMock = TestBed.inject(HttpTestingController);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
