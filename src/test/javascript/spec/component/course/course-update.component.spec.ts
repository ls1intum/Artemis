import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { Course } from 'app/entities/course.model';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
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
import { FileUploaderService, FileUploadResponse } from 'app/shared/http/file-uploader.service';
import { ImageCropperModule } from 'app/shared/image-cropper/image-cropper.module';
import { base64StringToBlob } from 'app/utils/blob-util';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

@Component({ selector: 'jhi-markdown-editor', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('Course Management Update Component', () => {
    let comp: CourseUpdateComponent;
    let fixture: ComponentFixture<CourseUpdateComponent>;
    let service: CourseManagementService;
    let profileService: ProfileService;
    let organizationService: OrganizationManagementService;
    let course: Course;
    let fileUploaderService: FileUploaderService;
    let uploadStub: jest.SpyInstance;

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
        course.postsEnabled = true;
        course.registrationEnabled = true;
        course.registrationConfirmationMessage = 'testRegistrationConfirmationMessage';
        course.presentationScore = 16;
        course.color = 'testColor';
        course.courseIcon = 'testCourseIcon';

        const parentRoute = {
            data: of({ course }),
        } as any as ActivatedRoute;
        const route = { parent: parentRoute } as any as ActivatedRoute;
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ReactiveFormsModule), ImageCropperModule],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(TranslateService),
            ],
            declarations: [
                CourseUpdateComponent,
                MarkdownEditorStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(SecuredImageComponent),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(ColorSelectorComponent),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(TranslateDirective),
                MockPipe(RemoveKeysPipe),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseUpdateComponent);
                comp = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
                profileService = TestBed.inject(ProfileService);
                organizationService = TestBed.inject(OrganizationManagementService);
                fileUploaderService = TestBed.inject(FileUploaderService);
                uploadStub = jest.spyOn(fileUploaderService, 'uploadFile');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('ngOnInit', () => {
        it('should get course, profile and fill the form', fakeAsync(() => {
            const profileInfo = { inProduction: false } as ProfileInfo;
            const profileInfoSubject = new BehaviorSubject<ProfileInfo>(profileInfo).asObservable();
            const getProfileStub = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfoSubject);
            const organization = new Organization();
            organization.id = 12344;
            const getOrganizationsStub = jest.spyOn(organizationService, 'getOrganizationsByCourse').mockReturnValue(of([organization]));

            comp.ngOnInit();
            expect(comp.course).toEqual(course);
            expect(comp.courseOrganizations).toEqual([organization]);
            expect(comp.presentationScoreEnabled).toBeTrue();
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
            expect(comp.courseForm.get(['postsEnabled'])?.value).toBe(course.postsEnabled);
            expect(comp.courseForm.get(['registrationEnabled'])?.value).toBe(course.registrationEnabled);
            expect(comp.courseForm.get(['registrationConfirmationMessage'])?.value).toBe(course.registrationConfirmationMessage);
            expect(comp.courseForm.get(['presentationScore'])?.value).toBe(course.presentationScore);
            expect(comp.courseForm.get(['color'])?.value).toBe(course.color);
            expect(comp.courseForm.get(['courseIcon'])?.value).toBe(course.courseIcon);
        }));
    });

    describe('save', () => {
        it('should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new Course();
            entity.id = 123;
            const updateStub = jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                id: new FormControl(entity.id),
                onlineCourse: new FormControl(entity.onlineCourse),
                registrationEnabled: new FormControl(entity.registrationEnabled),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                accuracyOfScores: new FormControl(entity.accuracyOfScores),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                maxComplaintTextLimit: new FormControl(entity.maxComplaintTextLimit),
                maxComplaintResponseTextLimit: new FormControl(entity.maxComplaintResponseTextLimit),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                postsEnabled: new FormControl(entity.postsEnabled),
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
            expect(updateStub).toHaveBeenCalledWith({ ...entity });
            expect(comp.isSaving).toBeFalse();
        }));

        it('should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new Course();
            const createStub = jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(entity.onlineCourse),
                registrationEnabled: new FormControl(entity.registrationEnabled),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                accuracyOfScores: new FormControl(entity.accuracyOfScores),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                maxComplaintTextLimit: new FormControl(entity.maxComplaintTextLimit),
                maxComplaintResponseTextLimit: new FormControl(entity.maxComplaintResponseTextLimit),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                postsEnabled: new FormControl(entity.postsEnabled),
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
            expect(createStub).toHaveBeenCalledWith({ ...entity });
            expect(comp.isSaving).toBeFalse();
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
            const event = { target: { files: fileList } };
            comp.setCourseImage(event);
            expect(comp.courseImageFile).toEqual(file);
            expect(comp.courseImageFileName).toBe('testFilename');
            expect(comp.imageChangedEvent).toBe(event);
        });
    });

    describe('imageLoaded', () => {
        it('should show cropper', () => {
            expect(comp.showCropper).toBeFalse();
            comp.imageLoaded();
            expect(comp.showCropper).toBeTrue();
        });
    });

    describe('uploadCourseImage', () => {
        let croppedImage: string;
        beforeEach(() => {
            croppedImage = 'testCroppedImage';
            comp.croppedImage = 'data:image/png;base64,' + comp.croppedImage;
            comp.courseImageFileName = 'testFilename';
            comp.showCropper = true;
            comp.ngOnInit();
        });
        it('should upload new image and update form', () => {
            uploadStub.mockResolvedValue({ path: 'testPath' } as FileUploadResponse);
            comp.uploadCourseImage();
            const file = base64StringToBlob(croppedImage, 'image/*');
            file['name'] = comp.courseImageFileName;
            expect(uploadStub.mock.calls[0][1]).toBe(comp.courseImageFileName);
            expect(comp.showCropper).toBeFalse();
        });
        it('should set image name to course icon if upload fails', () => {
            uploadStub.mockRejectedValue({} as FileUploadResponse);
            comp.course = new Course();
            comp.course.courseIcon = 'testCourseIcon';
            comp.uploadCourseImage();
            expect(uploadStub.mock.calls[0][1]).toBe(comp.courseImageFileName);
            expect(comp.courseImageFileName).toBe(comp.course.courseIcon);
            expect(comp.showCropper).toBeFalse();
        });
    });

    describe('changePresentationScoreInput', () => {
        it('should enabled if control is disabled', () => {
            const control = new FormControl(12);
            control.disable();
            comp.courseForm = new FormGroup({
                presentationScore: control,
            });
            expect(comp.courseForm.controls['presentationScore'].disabled).toBeTrue();
            comp.changePresentationScoreInput();
            expect(comp.courseForm.controls['presentationScore'].disabled).toBeFalse();
            expect(comp.presentationScoreEnabled).toBeTrue();
        });
        it('should reset if control has value', () => {
            const control = new FormControl(12);
            comp.courseForm = new FormGroup({
                presentationScore: control,
            });
            expect(comp.courseForm.controls['presentationScore'].disabled).toBeFalse();
            comp.changePresentationScoreInput();
            expect(comp.courseForm.controls['presentationScore'].disabled).toBeTrue();
            expect(comp.courseForm.controls['presentationScore'].value).toBe(0);
            expect(comp.presentationScoreEnabled).toBeFalse();
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
            comp.course.registrationEnabled = false;
            comp.courseForm = new FormGroup({
                registrationEnabled: new FormControl(false),
                onlineCourse: new FormControl(true),
            });
            expect(comp.courseForm.controls['registrationEnabled'].value).toBeFalse();
            expect(comp.courseForm.controls['onlineCourse'].value).toBeTrue();
            comp.changeRegistrationEnabled();
            expect(comp.courseForm.controls['onlineCourse'].value).toBeFalse();
            expect(comp.courseForm.controls['registrationEnabled'].value).toBeTrue();
            expect(comp.course.registrationEnabled).toBeTrue();
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
            expect(comp.courseForm.controls['maxComplaintTextLimit'].value).toBe(0);
            expect(comp.courseForm.controls['maxComplaintResponseTextLimit'].value).toBe(0);
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

    describe('getSemesters', () => {
        it('should get semesters around current year', () => {
            const years = dayjs().year() - 2018 + 1;
            const semesters = comp.getSemesters();
            expect(semesters[0]).toBe('');
            for (let i = 0; i <= years; i++) {
                expect(semesters[2 * i + 1]).toBe('SS' + (18 + i));
                expect(semesters[2 * i + 2]).toBe('WS' + (18 + i) + '/' + (19 + i));
            }
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
        it('should remove icon image and delete icon button from component', () => {
            setIcon();
            let deleteIconButton = getDeleteIconButton();
            deleteIconButton.dispatchEvent(new Event('delete'));
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
            const croppedImage = 'testCroppedImage';
            comp.croppedImage = 'data:image/png;base64,' + croppedImage;
            comp.courseImageFileName = 'testFilename';
            comp.showCropper = true;
            comp.ngOnInit();
            fixture.detectChanges();
        }

        function getDeleteIconButton() {
            return fixture.debugElement.nativeElement.querySelector('#delete-course-icon');
        }
    });
});
