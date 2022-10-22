import { ActivatedRoute, Router } from '@angular/router';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { Observable } from 'rxjs';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import dayjs from 'dayjs/esm';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { LOGIN_PATTERN, SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { Organization } from 'app/entities/organization.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { faBan, faExclamationTriangle, faQuestionCircle, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { base64StringToBlob } from 'app/utils/blob-util';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-course-update',
    templateUrl: './course-update.component.html',
    styleUrls: ['./course-update.component.scss'],
})
export class CourseUpdateComponent implements OnInit {
    CachingStrategy = CachingStrategy;
    ProgrammingLanguage = ProgrammingLanguage;

    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    courseForm: FormGroup;
    onlineCourseConfigurationForm: FormGroup;
    course: Course;
    isSaving: boolean;
    courseImageFile?: Blob | File;
    courseImageFileName: string;
    isUploadingCourseImage: boolean;
    imageChangedEvent: any = '';
    croppedImage: any = '';
    showCropper = false;
    presentationScoreEnabled = false;
    complaintsEnabled = true; // default value
    requestMoreFeedbackEnabled = true; // default value
    customizeGroupNames = false; // default value
    presentationScorePattern = /^[0-9]{0,4}$/; // makes sure that the presentation score is a positive natural integer greater than 0 and not too large
    courseOrganizations: Organization[];

    // Icons
    faSave = faSave;
    faBan = faBan;
    faTimes = faTimes;
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;

    // NOTE: These constants are used to define the maximum length of complaints and complaint responses.
    // This is the maximum value allowed in our database. These values must be the same as in Constants.java
    // Currently set to 65535 as this is the limit of TEXT
    readonly COMPLAINT_RESPONSE_TEXT_LIMIT = 65535;
    readonly COMPLAINT_TEXT_LIMIT = 65535;

    constructor(
        private courseService: CourseManagementService,
        private activatedRoute: ActivatedRoute,
        private fileUploaderService: FileUploaderService,
        private alertService: AlertService,
        private profileService: ProfileService,
        private organizationService: OrganizationManagementService,
        private modalService: NgbModal,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        // create a new course, and only overwrite it if we fetch a course to edit
        this.course = new Course();
        this.activatedRoute.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.organizationService.getOrganizationsByCourse(course.id).subscribe((organizations) => {
                    this.courseOrganizations = organizations;
                });

                // complaints are only enabled when at least one complaint is allowed and the complaint duration is positive
                this.complaintsEnabled =
                    (this.course.maxComplaints! > 0 || this.course.maxTeamComplaints! > 0) &&
                    this.course.maxComplaintTimeDays! > 0 &&
                    this.course.maxComplaintTextLimit! > 0 &&
                    this.course.maxComplaintResponseTextLimit! > 0;
                this.requestMoreFeedbackEnabled = this.course.maxRequestMoreFeedbackTimeDays! > 0;
            }
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                if (profileInfo.inProduction) {
                    // in production mode, the groups should not be customized by default when creating a course
                    // when editing a course, only admins can customize groups automatically
                    this.customizeGroupNames = !!this.course.id;
                } else {
                    // developers typically want to customize the groups, therefore this is prefilled
                    this.customizeGroupNames = true;
                    if (!this.course.studentGroupName) {
                        this.course.studentGroupName = 'artemis-dev';
                    }
                    if (!this.course.teachingAssistantGroupName) {
                        this.course.teachingAssistantGroupName = 'artemis-dev';
                    }
                    if (!this.course.editorGroupName) {
                        this.course.editorGroupName = 'artemis-dev';
                    }
                    if (!this.course.instructorGroupName) {
                        this.course.instructorGroupName = 'artemis-dev';
                    }
                }
            }
        });

        this.onlineCourseConfigurationForm = new FormGroup({
            id: new FormControl(this.course.onlineCourseConfiguration?.id),
            course: new FormControl(this.course),
            ltiKey: new FormControl(this.course.onlineCourseConfiguration?.ltiKey),
            ltiSecret: new FormControl(this.course.onlineCourseConfiguration?.ltiSecret),
            userPrefix: new FormControl(this.course.onlineCourseConfiguration?.userPrefix, { validators: [regexValidator(LOGIN_PATTERN)] }),
            registrationId: new FormControl(this.course.onlineCourseConfiguration?.registrationId),
            clientId: new FormControl(this.course.onlineCourseConfiguration?.clientId),
            authorizationUri: new FormControl(this.course.onlineCourseConfiguration?.authorizationUri),
            tokenUri: new FormControl(this.course.onlineCourseConfiguration?.tokenUri),
            jwkSetUri: new FormControl(this.course.onlineCourseConfiguration?.jwkSetUri),
        });

        this.courseForm = new FormGroup(
            {
                id: new FormControl(this.course.id),
                title: new FormControl(this.course.title, [Validators.required]),
                shortName: new FormControl(this.course.shortName, {
                    validators: [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)],
                    updateOn: 'blur',
                }),
                // note: we still reference them here so that they are used in the update method when the course is retrieved from the course form
                customizeGroupNames: new FormControl(this.customizeGroupNames),
                studentGroupName: new FormControl(this.course.studentGroupName),
                teachingAssistantGroupName: new FormControl(this.course.teachingAssistantGroupName),
                editorGroupName: new FormControl(this.course.editorGroupName),
                instructorGroupName: new FormControl(this.course.instructorGroupName),
                description: new FormControl(this.course.description),
                organizations: new FormControl(this.courseOrganizations),
                startDate: new FormControl(this.course.startDate),
                endDate: new FormControl(this.course.endDate),
                semester: new FormControl(this.course.semester),
                testCourse: new FormControl(this.course.testCourse),
                onlineCourse: new FormControl(this.course.onlineCourse),
                complaintsEnabled: new FormControl(this.complaintsEnabled),
                requestMoreFeedbackEnabled: new FormControl(this.requestMoreFeedbackEnabled),
                maxPoints: new FormControl(this.course.maxPoints, {
                    validators: [Validators.min(1)],
                }),
                accuracyOfScores: new FormControl(this.course.accuracyOfScores, {
                    validators: [Validators.min(1)],
                }),
                defaultProgrammingLanguage: new FormControl(this.course.defaultProgrammingLanguage),
                maxComplaints: new FormControl(this.course.maxComplaints, {
                    validators: [Validators.required, Validators.min(0)],
                }),
                maxTeamComplaints: new FormControl(this.course.maxTeamComplaints, {
                    validators: [Validators.required, Validators.min(0)],
                }),
                maxComplaintTimeDays: new FormControl(this.course.maxComplaintTimeDays, {
                    validators: [Validators.required, Validators.min(0)],
                }),
                maxComplaintTextLimit: new FormControl(this.course.maxComplaintTextLimit, {
                    validators: [Validators.required, Validators.min(0), Validators.max(this.COMPLAINT_TEXT_LIMIT)],
                }),
                maxComplaintResponseTextLimit: new FormControl(this.course.maxComplaintResponseTextLimit, {
                    validators: [Validators.required, Validators.min(0), Validators.max(this.COMPLAINT_RESPONSE_TEXT_LIMIT)],
                }),
                maxRequestMoreFeedbackTimeDays: new FormControl(this.course.maxRequestMoreFeedbackTimeDays, {
                    validators: [Validators.required, Validators.min(0)],
                }),
                postsEnabled: new FormControl(this.course.postsEnabled),
                registrationEnabled: new FormControl(this.course.registrationEnabled),
                registrationConfirmationMessage: new FormControl(this.course.registrationConfirmationMessage, {
                    validators: [Validators.maxLength(2000)],
                }),
                presentationScore: new FormControl({ value: this.course.presentationScore, disabled: this.course.presentationScore === 0 }, [
                    Validators.min(1),
                    regexValidator(this.presentationScorePattern),
                ]),
                color: new FormControl(this.course.color),
                courseIcon: new FormControl(this.course.courseIcon),
            },
            { validators: CourseValidator },
        );
        this.courseImageFileName = this.course.courseIcon!;
        this.croppedImage = this.course.courseIcon ? this.course.courseIcon : '';
        this.presentationScoreEnabled = this.course.presentationScore !== 0;
    }

    /**
     * Returns to previous state (same as back button in the browser)
     * Returns to the detail page if there is no previous state, and we edited an existing course
     * Returns to the overview page if there is no previous state, and we created a new course
     */
    previousState() {
        this.navigationUtilService.navigateBackWithOptional(['course-management'], this.course.id?.toString());
    }

    /**
     * Save the changes on a course
     * This function is called by pressing save after creating or editing a course
     */
    save() {
        this.isSaving = true;
        if (this.courseForm.controls['organizations'] !== undefined) {
            this.courseForm.controls['organizations'].setValue(this.courseOrganizations);
        }

        const course = this.courseForm.getRawValue();
        course.onlineCourseConfiguration = this.isOnlineCourse() ? this.onlineCourseConfigurationForm.getRawValue() : null;

        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(this.courseService.update(course));
        } else {
            this.subscribeToSaveResponse(this.courseService.create(course));
        }
    }

    openColorSelector(event: MouseEvent) {
        this.colorSelector.openColorSelector(event);
    }

    onSelectedColor(selectedColor: string) {
        this.courseForm.patchValue({ color: selectedColor });
    }

    /**
     * Async response after saving a course, handles appropriate action in case of error
     * @param result The Http response from the server
     */
    private subscribeToSaveResponse(result: Observable<HttpResponse<Course>>) {
        result.subscribe({
            next: (response: HttpResponse<Course>) => this.onSaveSuccess(response.body),
            error: (res: HttpErrorResponse) => this.onSaveError(res),
        });
    }

    /**
     * Action on successful course creation or edit
     */
    private onSaveSuccess(updatedCourse: Course | null) {
        this.isSaving = false;
        this.router.navigate(['course-management', updatedCourse?.id?.toString()]);
    }

    /**
     * @function set course icon
     * @param event {object} Event object which contains the uploaded file
     */
    setCourseImage(event: any): void {
        this.imageChangedEvent = event;
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            this.courseImageFile = fileList[0];
            this.courseImageFileName = this.courseImageFile.name;
        }
    }

    /**
     * @param event
     */
    imageCropped(event: ImageCroppedEvent) {
        this.croppedImage = event.base64;
    }

    imageLoaded() {
        this.showCropper = true;
    }

    /**
     * @function uploadBackground
     * @desc Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadCourseImage(): void {
        const contentType = 'image/*';
        const base64Data = this.croppedImage.replace('data:image/png;base64,', '');
        const file = base64StringToBlob(base64Data, contentType);
        const fileName = this.courseImageFileName;

        this.isUploadingCourseImage = true;
        this.fileUploaderService.uploadFile(file, fileName).then(
            (response) => {
                this.courseForm.patchValue({ courseIcon: response.path });
                this.isUploadingCourseImage = false;
                this.courseImageFile = undefined;
                this.courseImageFileName = response.path!;
            },
            () => {
                this.isUploadingCourseImage = false;
                this.courseImageFile = undefined;
                this.courseImageFileName = this.course.courseIcon!;
            },
        );
        this.showCropper = false;
    }

    /**
     * Action on unsuccessful course creation or edit
     * @param error The error for providing feedback
     */
    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
        if (errorMessage) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
        }

        this.isSaving = false;
        window.scrollTo(0, 0);
    }

    get shortName() {
        return this.courseForm.get('shortName')!;
    }

    get userPrefix() {
        return this.onlineCourseConfigurationForm.get('userPrefix')!;
    }

    /**
     * Enable or disable presentation score input field based on presentationScoreEnabled checkbox
     */
    changePresentationScoreInput() {
        const presentationScoreControl = this.courseForm.controls['presentationScore'];
        if (presentationScoreControl.disabled) {
            presentationScoreControl.enable();
            this.presentationScoreEnabled = true;
        } else {
            presentationScoreControl.reset({ value: 0, disabled: true });
            this.presentationScoreEnabled = false;
        }
    }

    /**
     * Enable or disable online course
     */
    changeOnlineCourse() {
        this.course.onlineCourse = !this.course.onlineCourse;
        if (this.course.onlineCourse) {
            // registration enabled cannot be activated if online course is active
            this.courseForm.controls['registrationEnabled'].setValue(false);
        }
        this.courseForm.controls['onlineCourse'].setValue(this.course.onlineCourse);
    }
    /**
     * Enable or disable student course registration
     */
    changeRegistrationEnabled() {
        this.course.registrationEnabled = !this.course.registrationEnabled;
        if (this.course.registrationEnabled) {
            // online course cannot be activated if registration enabled is set
            this.courseForm.controls['onlineCourse'].setValue(false);
        }
        this.courseForm.controls['registrationEnabled'].setValue(this.course.registrationEnabled);
    }

    /**
     * Enable or disable complaints
     */
    changeComplaintsEnabled() {
        if (!this.complaintsEnabled) {
            this.complaintsEnabled = true;
            this.courseForm.controls['maxComplaints'].setValue(3);
            this.courseForm.controls['maxTeamComplaints'].setValue(3);
            this.courseForm.controls['maxComplaintTimeDays'].setValue(7);
            this.courseForm.controls['maxComplaintTextLimit'].setValue(2000);
            this.courseForm.controls['maxComplaintResponseTextLimit'].setValue(2000);
        } else {
            this.complaintsEnabled = false;
            this.courseForm.controls['maxComplaints'].setValue(0);
            this.courseForm.controls['maxTeamComplaints'].setValue(0);
            this.courseForm.controls['maxComplaintTimeDays'].setValue(0);
            this.courseForm.controls['maxComplaintTextLimit'].setValue(0);
            this.courseForm.controls['maxComplaintResponseTextLimit'].setValue(0);
        }
    }

    /**
     * Enable or disable complaints
     */
    changeRequestMoreFeedbackEnabled() {
        if (!this.requestMoreFeedbackEnabled) {
            this.requestMoreFeedbackEnabled = true;
            this.courseForm.controls['maxRequestMoreFeedbackTimeDays'].setValue(7);
        } else {
            this.requestMoreFeedbackEnabled = false;
            this.courseForm.controls['maxRequestMoreFeedbackTimeDays'].setValue(0);
        }
    }

    /**
     * Enable or disable the customization of groups
     */
    changeCustomizeGroupNames() {
        if (!this.customizeGroupNames) {
            this.customizeGroupNames = true;
            this.courseForm.controls['studentGroupName'].setValue('artemis-dev');
            this.courseForm.controls['teachingAssistantGroupName'].setValue('artemis-dev');
            this.courseForm.controls['editorGroupName'].setValue('artemis-dev');
            this.courseForm.controls['instructorGroupName'].setValue('artemis-dev');
        } else {
            this.customizeGroupNames = false;
            this.courseForm.controls['studentGroupName'].setValue(undefined);
            this.courseForm.controls['teachingAssistantGroupName'].setValue(undefined);
            this.courseForm.controls['editorGroupName'].setValue(undefined);
            this.courseForm.controls['instructorGroupName'].setValue(undefined);
        }
    }

    /**
     * Enable or disable test course
     */
    changeTestCourseEnabled() {
        this.course.testCourse = !this.course.testCourse;
    }

    /**
     * Returns a string array of possible semester values
     */
    getSemesters() {
        // 2018 is the first year we offer semesters for and go one year into the future
        const years = dayjs().year() - 2018 + 1;
        // Add an empty semester as default value
        const semesters: string[] = [''];
        for (let i = 0; i <= years; i++) {
            semesters[2 * i + 1] = 'SS' + (18 + i);
            semesters[2 * i + 2] = 'WS' + (18 + i) + '/' + (19 + i);
        }
        return semesters;
    }

    /**
     * Opens the organizations modal used to select an organization to add
     */
    openOrganizationsModal() {
        const modalRef = this.modalService.open(OrganizationSelectorComponent, { size: 'xl', backdrop: 'static' });
        modalRef.componentInstance.organizations = this.courseOrganizations;
        modalRef.closed.subscribe((organization) => {
            if (organization !== undefined) {
                if (this.courseOrganizations === undefined) {
                    this.courseOrganizations = [];
                }
                this.courseOrganizations.push(organization);
            }
        });
    }

    /**
     * Removes an organization from the course
     * @param organization to remove
     */
    removeOrganizationFromCourse(organization: Organization) {
        this.courseOrganizations = this.courseOrganizations.filter((o) => o.id !== organization.id);
    }

    /**
     * Updates registrationConfirmationMessage on markdown change
     * @param message new registrationConfirmationMessage
     */
    updateRegistrationConfirmationMessage(message: string) {
        this.courseForm.controls['registrationConfirmationMessage'].setValue(message);
    }

    /**
     * Auxiliary method checking if online course is currently true
     */
    isOnlineCourse(): boolean {
        return this.courseForm.controls['onlineCourse'].value === true;
    }

    /**
     * Returns whether the dates are valid or not
     * @return true if the dats are valid
     */
    get isValidDate(): boolean {
        // allow instructors to set startDate and endDate later
        if (this.atLeastOneDateNotExisting()) {
            return true;
        }
        return dayjs(this.course.startDate).isBefore(this.course.endDate);
    }

    /**
     * Auxiliary method checking if at least one date is not set or simply deleted by the user
     * @private
     */
    private atLeastOneDateNotExisting(): boolean {
        // we need to take into account that the date is only deleted by the user, which leads to a invalid state of the date
        return !this.course.startDate || !this.course.endDate || !this.course.startDate.isValid() || !this.course.endDate.isValid();
    }

    get isValidConfiguration(): boolean {
        return this.isValidDate;
    }
}

const CourseValidator: ValidatorFn = (formGroup: FormGroup) => {
    const onlineCourse = formGroup.controls['onlineCourse'].value;
    const registrationEnabled = formGroup.controls['registrationEnabled'].value;
    // it cannot be the case that both values are true
    return onlineCourse != undefined && registrationEnabled != undefined && !(onlineCourse && registrationEnabled) ? null : { range: true };
};
