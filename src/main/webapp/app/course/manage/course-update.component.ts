import { ActivatedRoute, Router } from '@angular/router';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { Observable, OperatorFunction, Subject, debounceTime, distinctUntilChanged, filter, map, merge, tap } from 'rxjs';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { Course, CourseInformationSharingConfiguration, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ARTEMIS_DEFAULT_COLOR, PROFILE_ATHENA, PROFILE_LTI } from 'app/app.constants';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import dayjs from 'dayjs/esm';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { Organization } from 'app/entities/organization.model';
import { NgbModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { OrganizationSelectorComponent } from 'app/shared/organization-selector/organization-selector.component';
import { faBan, faExclamationTriangle, faPen, faQuestionCircle, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { base64StringToBlob } from 'app/utils/blob-util';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { CourseAdminService } from 'app/course/manage/course-admin.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { FileService } from 'app/shared/http/file.service';
import { onError } from 'app/shared/util/global.utils';
import { getSemesters } from 'app/utils/semester-utils';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';
import { scrollToTopOfPage } from 'app/shared/util/utils';

const DEFAULT_CUSTOM_GROUP_NAME = 'artemis-dev';

@Component({
    selector: 'jhi-course-update',
    templateUrl: './course-update.component.html',
    styleUrls: ['./course-update.component.scss'],
})
export class CourseUpdateComponent implements OnInit {
    CachingStrategy = CachingStrategy;
    ProgrammingLanguage = ProgrammingLanguage;

    @ViewChild('timeZoneInput') tzTypeAhead: NgbTypeahead;
    tzFocus$ = new Subject<string>();
    tzClick$ = new Subject<string>();
    timeZones: string[] = [];
    originalTimeZone?: string;

    @ViewChild('fileInput', { static: false }) fileInput: ElementRef<HTMLInputElement>;
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    courseForm: FormGroup;
    course: Course;
    isSaving: boolean;
    courseImageUploadFile?: File;
    croppedImage?: string;
    complaintsEnabled = true;
    requestMoreFeedbackEnabled = true;
    customizeGroupNames = false;
    courseOrganizations: Organization[];
    isAdmin = false;
    // Icons
    faSave = faSave;
    faBan = faBan;
    faTimes = faTimes;
    faTrash = faTrash;
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faPen = faPen;

    faqEnabled = true;
    communicationEnabled = true;
    messagingEnabled = true;
    ltiEnabled = false;
    isAthenaEnabled = false;
    tutorialGroupsFeatureActivated = false;

    readonly semesters = getSemesters();

    // NOTE: These constants are used to define the maximum length of complaints and complaint responses.
    // This is the maximum value allowed in our database. These values must be the same as in Constants.java
    // Currently set to 65535 as this is the limit of TEXT
    readonly COMPLAINT_RESPONSE_TEXT_LIMIT = 65535;
    readonly COMPLAINT_TEXT_LIMIT = 65535;

    readonly COURSE_TITLE_LIMIT = 255;

    constructor(
        private eventManager: EventManager,
        private courseManagementService: CourseManagementService,
        private courseAdminService: CourseAdminService,
        private activatedRoute: ActivatedRoute,
        private fileService: FileService,
        private alertService: AlertService,
        private profileService: ProfileService,
        private organizationService: OrganizationManagementService,
        private modalService: NgbModal,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
        private featureToggleService: FeatureToggleService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.timeZones = (Intl as any).supportedValuesOf('timeZone');
        this.isSaving = false;
        // create a new course, and only overwrite it if we fetch a course to edit
        this.course = new Course();
        this.activatedRoute.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.organizationService.getOrganizationsByCourse(course.id).subscribe((organizations) => {
                    this.courseOrganizations = organizations;
                });
                this.originalTimeZone = this.course.timeZone;
                this.faqEnabled = course.faqEnabled;
                // complaints are only enabled when at least one complaint is allowed and the complaint duration is positive
                this.complaintsEnabled =
                    (this.course.maxComplaints! > 0 || this.course.maxTeamComplaints! > 0) &&
                    this.course.maxComplaintTimeDays! > 0 &&
                    this.course.maxComplaintTimeDays! > 0 &&
                    this.course.maxComplaintTextLimit! > 0 &&
                    this.course.maxComplaintResponseTextLimit! > 0;
                this.requestMoreFeedbackEnabled = this.course.maxRequestMoreFeedbackTimeDays! > 0;
            } else {
                this.fileService.getTemplateCodeOfCondcut().subscribe({
                    next: (res: HttpResponse<string>) => {
                        if (res.body) {
                            this.course.courseInformationSharingMessagingCodeOfConduct = res.body;
                        }
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
            }
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                if (!profileInfo.inProduction) {
                    // developers may want to customize the groups
                    this.customizeGroupNames = true;
                    if (!this.course.studentGroupName) {
                        this.course.studentGroupName = DEFAULT_CUSTOM_GROUP_NAME;
                    }
                    if (!this.course.teachingAssistantGroupName) {
                        this.course.teachingAssistantGroupName = DEFAULT_CUSTOM_GROUP_NAME;
                    }
                    if (!this.course.editorGroupName) {
                        this.course.editorGroupName = DEFAULT_CUSTOM_GROUP_NAME;
                    }
                    if (!this.course.instructorGroupName) {
                        this.course.instructorGroupName = DEFAULT_CUSTOM_GROUP_NAME;
                    }
                }
                this.ltiEnabled = profileInfo.activeProfiles.includes(PROFILE_LTI);
                this.isAthenaEnabled = profileInfo.activeProfiles.includes(PROFILE_ATHENA);
            }
        });

        this.communicationEnabled = isCommunicationEnabled(this.course);
        this.messagingEnabled = isMessagingEnabled(this.course);

        this.courseForm = new FormGroup(
            {
                id: new FormControl(this.course.id),
                title: new FormControl(this.course.title, {
                    validators: [Validators.required, Validators.maxLength(this.COURSE_TITLE_LIMIT)],
                    updateOn: 'blur',
                }),
                shortName: new FormControl(
                    { value: this.course.shortName, disabled: !!this.course.id },
                    {
                        validators: [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)],
                        updateOn: 'blur',
                    },
                ),
                // note: we still reference them here so that they are used in the update method when the course is retrieved from the course form
                customizeGroupNames: new FormControl(this.customizeGroupNames),
                studentGroupName: new FormControl(this.course.studentGroupName),
                teachingAssistantGroupName: new FormControl(this.course.teachingAssistantGroupName),
                editorGroupName: new FormControl(this.course.editorGroupName),
                instructorGroupName: new FormControl(this.course.instructorGroupName),
                description: new FormControl(this.course.description),
                courseInformationSharingMessagingCodeOfConduct: new FormControl(this.course.courseInformationSharingMessagingCodeOfConduct),
                organizations: new FormControl(this.courseOrganizations),
                startDate: new FormControl(this.course.startDate),
                endDate: new FormControl(this.course.endDate),
                semester: new FormControl(this.course.semester),
                testCourse: new FormControl(this.course.testCourse),
                learningPathsEnabled: new FormControl(this.course.learningPathsEnabled),
                studentCourseAnalyticsDashboardEnabled: new FormControl(this.course.studentCourseAnalyticsDashboardEnabled),
                onlineCourse: new FormControl(this.course.onlineCourse),
                complaintsEnabled: new FormControl(this.complaintsEnabled),
                faqEnabled: new FormControl(this.faqEnabled),
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
                restrictedAthenaModulesAccess: new FormControl(this.course.restrictedAthenaModulesAccess),
                enrollmentEnabled: new FormControl(this.course.enrollmentEnabled),
                enrollmentStartDate: new FormControl(this.course.enrollmentStartDate),
                enrollmentEndDate: new FormControl(this.course.enrollmentEndDate),
                enrollmentConfirmationMessage: new FormControl(this.course.enrollmentConfirmationMessage, {
                    validators: [Validators.maxLength(2000)],
                }),
                unenrollmentEnabled: new FormControl(this.course.unenrollmentEnabled),
                unenrollmentEndDate: new FormControl(this.course.unenrollmentEndDate),
                color: new FormControl(this.course.color),
                courseIcon: new FormControl(this.course.courseIcon),
            },
            { validators: CourseValidator },
        );
        this.croppedImage = this.course.courseIcon;

        this.featureToggleService
            .getFeatureToggleActive(FeatureToggle.TutorialGroups)
            .pipe(
                tap((active) => {
                    this.tutorialGroupsFeatureActivated = active;
                }),
            )
            .subscribe(() => {
                if (this.tutorialGroupsFeatureActivated && this.courseForm) {
                    this.courseForm.addControl('timeZone', new FormControl(this.course?.timeZone));
                }
            });

        this.isAdmin = this.accountService.isAdmin();
    }
    tzResultFormatter = (timeZone: string) => timeZone;
    tzInputFormatter = (timeZone: string) => timeZone;

    tzSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.tzClick$.pipe(filter(() => !this.tzTypeAhead.isPopupOpen()));
        const inputFocus$ = this.tzFocus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term.length < 3 ? [] : this.timeZones.filter((tz) => tz.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    get timeZoneChanged() {
        return this.course?.id && this.originalTimeZone && this.originalTimeZone !== this.courseForm.value.timeZone;
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
        let file = undefined;
        if (this.courseImageUploadFile && this.croppedImage) {
            const base64Data = this.croppedImage.replace('data:image/png;base64,', '');
            file = base64StringToBlob(base64Data, 'image/*');
        }

        const course = this.courseForm.getRawValue();

        if (this.communicationEnabled && this.messagingEnabled) {
            course['courseInformationSharingConfiguration'] = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        } else if (this.communicationEnabled && !this.messagingEnabled) {
            course['courseInformationSharingConfiguration'] = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        } else {
            this.communicationEnabled = false;
            course['courseInformationSharingConfiguration'] = CourseInformationSharingConfiguration.DISABLED;
        }

        if (!course.enrollmentEnabled) {
            course.enrollmentConfirmationMessage = undefined;
        }

        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(this.courseManagementService.update(this.course.id, course, file));
        } else {
            this.subscribeToSaveResponse(this.courseAdminService.create(course, file));
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

        if (this.course != updatedCourse) {
            this.eventManager.broadcast({
                name: 'courseModification',
                content: 'Changed a course',
            });
        }

        this.router.navigate(['course-management', updatedCourse?.id?.toString()]);
        scrollToTopOfPage();
    }

    /**
     * @function set course icon
     * @param event {object} Event object which contains the uploaded file
     */
    setCourseImage(event: Event): void {
        const element = event.currentTarget as HTMLInputElement;
        if (element.files && element.files.length > 0) {
            this.courseImageUploadFile = element.files[0];
            this.openCropper();
        }
        element.value = '';
    }

    /**
     * @param event
     */
    imageCropped(event: ImageCroppedEvent) {
        this.croppedImage = event.base64;
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

    /**
     * Enable or disable online course
     */
    changeOnlineCourse() {
        this.course.onlineCourse = !this.course.onlineCourse;
        if (this.course.onlineCourse) {
            // enrollment enabled cannot be activated if online course is active
            this.courseForm.controls['enrollmentEnabled'].setValue(false);
        }
        this.courseForm.controls['onlineCourse'].setValue(this.course.onlineCourse);
    }
    /**
     * Enable or disable student course enrollment
     */
    changeEnrollmentEnabled() {
        this.course.enrollmentEnabled = !this.course.enrollmentEnabled;
        if (this.course.enrollmentEnabled) {
            // online course cannot be activated if enrollment enabled is set
            this.courseForm.controls['onlineCourse'].setValue(false);
            if (!this.course.enrollmentStartDate || !this.course.enrollmentEndDate) {
                this.course.enrollmentStartDate = this.course.startDate;
                this.courseForm.controls['enrollmentStartDate'].setValue(this.course.startDate);
                this.course.enrollmentEndDate = this.course.endDate;
                this.courseForm.controls['enrollmentEndDate'].setValue(this.course.endDate);
            }
        } else {
            if (this.course.unenrollmentEnabled) {
                this.changeUnenrollmentEnabled();
            }
        }
        this.courseForm.controls['enrollmentEnabled'].setValue(this.course.enrollmentEnabled);
    }

    /**
     * Enable or disable student course unenrollment
     */
    changeUnenrollmentEnabled() {
        this.course.unenrollmentEnabled = !this.course.unenrollmentEnabled;
        this.courseForm.controls['unenrollmentEnabled'].setValue(this.course.unenrollmentEnabled);
        if (this.course.unenrollmentEnabled && !this.course.unenrollmentEndDate) {
            this.course.unenrollmentEndDate = this.course.endDate;
            this.courseForm.controls['unenrollmentEndDate'].setValue(this.course.unenrollmentEndDate);
        }
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
            this.courseForm.controls['maxComplaintTextLimit'].setValue(2000);
            this.courseForm.controls['maxComplaintResponseTextLimit'].setValue(2000);
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
            this.setGroupNameValuesInCourseForm(
                this.course.studentGroupName ?? DEFAULT_CUSTOM_GROUP_NAME,
                this.course.teachingAssistantGroupName ?? DEFAULT_CUSTOM_GROUP_NAME,
                this.course.editorGroupName ?? DEFAULT_CUSTOM_GROUP_NAME,
                this.course.instructorGroupName ?? DEFAULT_CUSTOM_GROUP_NAME,
            );
        } else {
            this.customizeGroupNames = false;
            if (!this.course.id) {
                // Creating: clear the values so groups are no longer customized
                this.setGroupNameValuesInCourseForm(undefined, undefined, undefined, undefined);
            } else {
                // Editing: restore the old values -> no change.
                this.setGroupNameValuesInCourseForm(
                    this.course.studentGroupName,
                    this.course.teachingAssistantGroupName,
                    this.course.editorGroupName,
                    this.course.instructorGroupName,
                );
            }
        }
    }

    private setGroupNameValuesInCourseForm(studentGroupName?: string, teachingAssistantGroupName?: string, editorGroupName?: string, instructorGroupName?: string) {
        this.courseForm.controls['studentGroupName'].setValue(studentGroupName);
        this.courseForm.controls['teachingAssistantGroupName'].setValue(teachingAssistantGroupName);
        this.courseForm.controls['editorGroupName'].setValue(editorGroupName);
        this.courseForm.controls['instructorGroupName'].setValue(instructorGroupName);
    }
    changeFaqEnabled() {
        this.faqEnabled = !this.faqEnabled;
        this.courseForm.controls['faqEnabled'].setValue(this.faqEnabled);
    }
    /**
     * Enable or disable test course
     */
    changeTestCourseEnabled() {
        this.course.testCourse = !this.course.testCourse;
    }

    changeRestrictedAthenaModulesEnabled() {
        this.course.restrictedAthenaModulesAccess = !this.course.restrictedAthenaModulesAccess;
        this.courseForm.controls['restrictedAthenaModulesAccess'].setValue(this.course.restrictedAthenaModulesAccess);
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
     * Updates enrollmentConfirmationMessage on markdown change
     * @param message new enrollmentConfirmationMessage
     */
    updateEnrollmentConfirmationMessage(message: string) {
        this.courseForm.controls['enrollmentConfirmationMessage'].setValue(message);
    }

    /**
     * Updates courseInformationSharingMessagingCodeOfConduct on markdown change
     * @param message new courseInformationSharingMessagingCodeOfConduct
     */
    updateCourseInformationSharingMessagingCodeOfConduct(message: string) {
        this.courseForm.controls['courseInformationSharingMessagingCodeOfConduct'].setValue(message);
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
     * Returns whether the enrollment start and end dates are valid
     * @return true if the dates are valid
     */
    get isValidEnrollmentPeriod(): boolean {
        // allow instructors to set enrollment dates later
        if (!this.course.enrollmentStartDate || !this.course.enrollmentEndDate) {
            return true;
        }

        // enrollment period requires configured start and end date of the course
        if (this.atLeastOneDateNotExisting() || !this.isValidDate) {
            return false;
        }

        return (
            dayjs(this.course.enrollmentStartDate).isBefore(this.course.enrollmentEndDate) &&
            !dayjs(this.course.enrollmentStartDate).isAfter(this.course.startDate) &&
            !dayjs(this.course.enrollmentEndDate).isAfter(this.course.endDate)
        );
    }

    /**
     * Returns whether the unenrollment end date is valid or not
     * @return true if the date is valid
     */
    get isValidUnenrollmentEndDate(): boolean {
        // allow instructors to set unenrollment end date later
        if (!this.course.unenrollmentEndDate) {
            return true;
        }

        // course enrollment period is required to configure unenrollment end date
        if (!this.course.enrollmentStartDate || !this.course.enrollmentEndDate || !this.isValidEnrollmentPeriod) {
            return false;
        }

        return !dayjs(this.course.unenrollmentEndDate).isBefore(this.course.enrollmentEndDate) && !dayjs(this.course.unenrollmentEndDate).isAfter(this.course.endDate);
    }

    /**
     * Auxiliary method checking if at least one date is not set or simply deleted by the user
     */
    private atLeastOneDateNotExisting(): boolean {
        // we need to take into account that the date is only deleted by the user, which leads to a invalid state of the date
        return !this.course.startDate || !this.course.endDate || !this.course.startDate.isValid() || !this.course.endDate.isValid();
    }

    get isValidConfiguration(): boolean {
        return this.isValidDate && this.isValidEnrollmentPeriod && this.isValidUnenrollmentEndDate;
    }

    /**
     * Deletes the course icon
     */
    deleteCourseIcon() {
        this.course.courseIcon = undefined;
        this.croppedImage = undefined;
        this.courseForm.controls['courseIcon'].setValue(undefined);
    }

    protected readonly FeatureToggle = FeatureToggle;

    triggerFileInput() {
        this.fileInput.nativeElement.click();
    }

    openCropper(): void {
        const modalRef = this.modalService.open(ImageCropperModalComponent, { size: 'm' });
        modalRef.componentInstance.uploadFile = this.courseImageUploadFile;
        modalRef.componentInstance.croppedImage = this.croppedImage;
        modalRef.result.then((result) => {
            if (result) {
                this.croppedImage = result;
            }
        });
    }

    disableMessaging() {
        this.messagingEnabled = false;
    }
}

const CourseValidator: ValidatorFn = (formGroup: FormGroup) => {
    const onlineCourse = formGroup.controls['onlineCourse'].value;
    const enrollmentEnabled = formGroup.controls['enrollmentEnabled'].value;
    // it cannot be the case that both values are true
    return onlineCourse != undefined && enrollmentEnabled != undefined && !(onlineCourse && enrollmentEnabled) ? null : { range: true };
};
