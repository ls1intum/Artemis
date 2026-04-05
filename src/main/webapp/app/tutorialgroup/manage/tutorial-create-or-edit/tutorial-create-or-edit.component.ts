import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { TutorialGroupDetailData, TutorialGroupTutor } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialEditLanguagesInputComponent } from 'app/tutorialgroup/manage/tutorial-edit-languages-input/tutorial-edit-languages-input.component';
import dayjs from 'dayjs/esm';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { Validation, ValidationStatus } from 'app/shared/util/validation';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { CreateOrUpdateTutorialGroupRequest } from 'app/openapi/model/createOrUpdateTutorialGroupRequest';
import { TutorialGroupSchedule } from 'app/openapi/model/tutorialGroupSchedule';

enum Mode {
    ONLINE = 'Online',
    OFFLINE = 'Offline',
}

export interface CreateTutorialGroupEvent {
    courseId: number;
    createTutorialGroupDTO: CreateOrUpdateTutorialGroupRequest;
}

export interface UpdateTutorialGroupEvent {
    courseId: number;
    tutorialGroupId: number;
    updateTutorialGroupDTO: CreateOrUpdateTutorialGroupRequest;
}

@Component({
    selector: 'jhi-tutorial-edit',
    imports: [
        InputGroupModule,
        InputGroupAddonModule,
        InputTextModule,
        FormsModule,
        ToggleSwitchModule,
        SelectModule,
        InputNumberModule,
        DatePickerModule,
        TooltipModule,
        ButtonModule,
        RouterLink,
        TutorialEditLanguagesInputComponent,
        ConfirmDialogModule,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    providers: [ConfirmationService],
    templateUrl: './tutorial-create-or-edit.component.html',
    styleUrl: './tutorial-create-or-edit.component.scss',
})
export class TutorialCreateOrEditComponent {
    private readonly titleRegex = /^[A-Za-z0-9][A-Za-z0-9: -]*$/;
    protected readonly ValidationStatus = ValidationStatus;
    private confirmationService = inject(ConfirmationService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private inputsInvalid = computed(() => this.computeIfInputsInvalid());

    courseId = input.required<number>();
    tutors = input.required<TutorialGroupTutor[]>();
    tutorialGroup = input<TutorialGroupDetailData>();
    schedule = input<TutorialGroupSchedule>();

    title = signal('');
    titleValidationResult = computed<Validation>(() => this.computeTitleValidation());
    titleInputTouched = signal(false);
    selectedTutorId = signal<number | undefined>(undefined);
    tutorValidationResult = computed<Validation>(() => this.computeTutorValidation());
    tutorInputTouched = signal(false);
    alreadyUsedLanguages = signal<string[]>([]);
    selectedLanguage = signal<string>('');
    languageValidationResult = signal<Validation>({ status: ValidationStatus.VALID });
    modes = Object.values(Mode);
    selectedMode = signal<Mode>(Mode.OFFLINE);
    campus = signal('');
    campusValidationResult = computed<Validation>(() => this.computeCampusValidation());
    capacity = signal<number | undefined>(undefined);
    additionalInformation = signal('');
    additionalInformationValidationResult = computed<Validation>(() => this.computeAdditionalInformationValidation());

    configureSessionPlan = signal(false);
    firstSessionStart = signal<Date | undefined>(undefined);
    firstSessionStartInputTouched = signal(false);
    firstSessionStartValidationResult = computed<Validation>(() => this.computeFirstSessionStartValidation());
    firstSessionEnd = signal<Date | undefined>(undefined);
    firstSessionEndInputTouched = signal(false);
    firstSessionEndValidationResult = computed<Validation>(() => this.computeFirstSessionEndValidation());
    repetitionFrequency = signal<number>(1);
    tutorialPeriodEnd = signal<Date | undefined>(undefined);
    tutorialPeriodEndInputTouched = signal(false);
    tutorialPeriodEndValidationResult = computed<Validation>(() => this.computeTeachingPeriodEndValidation());
    location = signal('');
    locationInputTouched = signal(false);
    locationValidationResult = computed<Validation>(() => this.computeLocationValidation());
    scheduleChangeOverwritesSessions = computed<boolean>(() => this.computeIfScheduleChangeOverwritesSessions());

    onUpdate = output<UpdateTutorialGroupEvent>();
    onCreate = output<CreateTutorialGroupEvent>();
    saveButtonDisabled = computed<boolean>(() => this.computeIfSaveButtonDisabled());
    isEditMode = computed<boolean>(() => this.tutorialGroup() !== undefined);

    constructor() {
        effect(() => {
            const tutorialGroup = this.tutorialGroup();
            if (tutorialGroup) {
                this.title.set(tutorialGroup.title);
                this.selectedTutorId.set(tutorialGroup.tutorId);
                this.selectedLanguage.set(tutorialGroup.language);
                this.selectedMode.set(tutorialGroup.isOnline ? Mode.ONLINE : Mode.OFFLINE);
                if (tutorialGroup.campus) {
                    this.campus.set(tutorialGroup.campus);
                }
                if (tutorialGroup.capacity) {
                    this.capacity.set(tutorialGroup.capacity);
                }
                if (tutorialGroup.additionalInformation) {
                    this.additionalInformation.set(tutorialGroup.additionalInformation);
                }
            }
        });
        effect(() => {
            const schedule = this.schedule();
            if (schedule) {
                this.firstSessionStart.set(dayjs(schedule.firstSessionStart).toDate());
                this.firstSessionEnd.set(dayjs(schedule.firstSessionEnd).toDate());
                this.repetitionFrequency.set(schedule.repetitionFrequency);
                this.tutorialPeriodEnd.set(dayjs(schedule.tutorialPeriodEnd).toDate());
                this.location.set(schedule.location);
                this.configureSessionPlan.set(true);
            }
        });
        effect(() => {
            this.tutorialGroupApiService.getUniqueLanguageValues(this.courseId(), 'body').subscribe({
                next: (languages) => {
                    this.alreadyUsedLanguages.set(languages);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.createOrEditTutorialGroup.networkError.fetchLanguages');
                },
            });
        });
    }

    save() {
        const courseId = this.courseId();
        if (this.tutorialGroup()) {
            const tutorialGroupId = this.tutorialGroup()?.id;
            if (!tutorialGroupId) return;
            const updateTutorialGroup = this.assembleCreateOrUpdateTutorialGroupRequest();
            if (this.scheduleChangeOverwritesSessions()) {
                this.confirmScheduleChangingSave(courseId, tutorialGroupId, updateTutorialGroup);
            } else {
                this.onUpdate.emit({ courseId: courseId, tutorialGroupId: tutorialGroupId, updateTutorialGroupDTO: updateTutorialGroup });
            }
        } else {
            const createTutorialGroupRequest = this.assembleCreateOrUpdateTutorialGroupRequest();
            this.onCreate.emit({ courseId: courseId, createTutorialGroupDTO: createTutorialGroupRequest });
        }
    }

    private confirmScheduleChangingSave(courseId: number, tutorialGroupId: number, updateTutorialGroupRequest: CreateOrUpdateTutorialGroupRequest) {
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.pages.createOrEditTutorialGroup.confirmSaveDialog.header'),
            message: this.translateService.instant('artemisApp.pages.createOrEditTutorialGroup.confirmSaveDialog.message'),
            acceptLabel: this.translateService.instant('artemisApp.pages.createOrEditTutorialGroup.confirmSaveDialog.acceptButtonLabel'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptButtonStyleClass: 'p-button-danger',
            rejectButtonStyleClass: 'p-button-secondary',
            accept: () => this.onUpdate.emit({ courseId, tutorialGroupId, updateTutorialGroupDTO: updateTutorialGroupRequest }),
        });
    }

    private assembleCreateOrUpdateTutorialGroupRequest(): CreateOrUpdateTutorialGroupRequest {
        const tutorialGroupSchedule: TutorialGroupSchedule | undefined = this.configureSessionPlan()
            ? {
                  firstSessionStart: dayjs(this.firstSessionStart()).format('YYYY-MM-DDTHH:mm:ss'),
                  firstSessionEnd: dayjs(this.firstSessionEnd()).format('YYYY-MM-DDTHH:mm:ss'),
                  repetitionFrequency: this.repetitionFrequency(),
                  tutorialPeriodEnd: dayjs(this.tutorialPeriodEnd()).format('YYYY-MM-DD'),
                  location: this.location(),
              }
            : undefined;
        return {
            title: this.title().trim(),
            tutorId: this.selectedTutorId()!,
            language: this.selectedLanguage().trim(),
            isOnline: this.selectedMode() === Mode.ONLINE,
            campus: this.campus().trim() || undefined,
            capacity: this.capacity(),
            additionalInformation: this.additionalInformation().trim() || undefined,
            tutorialGroupSchedule: tutorialGroupSchedule,
        };
    }

    private computeTitleValidation(): Validation {
        const title = this.title();
        if (!title.match(this.titleRegex)) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.titleContent',
            };
        }
        const trimmedTitle = title.trim();
        if (trimmedTitle.length > 19) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.titleLength',
            };
        }
        return {
            status: ValidationStatus.VALID,
        };
    }

    private computeTutorValidation(): Validation {
        const selectedTutorId = this.selectedTutorId();
        if (selectedTutorId) return { status: ValidationStatus.VALID };
        return {
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.tutorRequired',
        };
    }

    private computeCampusValidation(): Validation {
        const trimmedCampus = this.campus().trim();
        if (trimmedCampus && trimmedCampus.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.campusLength',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeAdditionalInformationValidation(): Validation {
        const trimmedAdditionalInformation = this.additionalInformation().trim();
        if (trimmedAdditionalInformation && trimmedAdditionalInformation.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.additionalInformationLength',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeFirstSessionStartValidation(): Validation {
        return this.firstSessionStart()
            ? { status: ValidationStatus.VALID }
            : {
                  status: ValidationStatus.INVALID,
                  message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionStartRequired',
              };
    }

    private computeFirstSessionEndValidation(): Validation {
        const firstSessionEnd = this.firstSessionEnd();
        if (!firstSessionEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndRequired',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && firstSessionEnd <= firstSessionStart) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndNotAfterStart',
            };
        }
        if (
            firstSessionStart &&
            (firstSessionStart.getFullYear() !== firstSessionEnd.getFullYear() ||
                firstSessionStart.getMonth() !== firstSessionEnd.getMonth() ||
                firstSessionStart.getDate() !== firstSessionEnd.getDate())
        ) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndNotOnSameDayAsStart',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeTeachingPeriodEndValidation(): Validation {
        const teachingPeriodEnd = this.tutorialPeriodEnd();
        if (!teachingPeriodEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodRequired',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && teachingPeriodEnd <= firstSessionStart) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodNotAfterFirstSessionStart',
            };
        }
        if (firstSessionStart && teachingPeriodEnd > dayjs(firstSessionStart).add(2, 'year').toDate()) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodMoreThanTwoYearsAfterFirstSessionStart',
            };
        }
        const firstSessionEnd = this.firstSessionEnd();
        if (firstSessionEnd && teachingPeriodEnd <= firstSessionEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodNotAfterFirstSessionEnd',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeLocationValidation(): Validation {
        const trimmedLocation = this.location().trim();
        if (!trimmedLocation) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.locationRequired',
            };
        }
        if (trimmedLocation.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.locationLength',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeIfScheduleChangeOverwritesSessions(): boolean {
        const schedule = this.schedule();
        if (!schedule) return false;
        const configureSessionPlan = this.configureSessionPlan();
        if (schedule && configureSessionPlan) {
            const firstSessionStartChanged = dayjs(this.firstSessionStart()).format('YYYY-MM-DDTHH:mm:ss') !== schedule.firstSessionStart;
            const firstSessionEndChanged = dayjs(this.firstSessionEnd()).format('YYYY-MM-DDTHH:mm:ss') !== schedule.firstSessionEnd;
            const repetitionFrequencyChanged = this.repetitionFrequency() !== schedule.repetitionFrequency;
            const tutorialPeriodEndChanged = dayjs(this.tutorialPeriodEnd()).format('YYYY-MM-DD') !== schedule.tutorialPeriodEnd;
            const locationChanged = this.location() !== schedule.location;
            return firstSessionStartChanged || firstSessionEndChanged || repetitionFrequencyChanged || tutorialPeriodEndChanged || locationChanged;
        }
        return true;
    }

    private computeIfSaveButtonDisabled(): boolean {
        if (this.inputsInvalid()) return true;
        const tutorialGroup = this.tutorialGroup();
        const schedule = this.schedule();
        if (tutorialGroup) {
            return !this.checkIfTutorialGroupChanged(tutorialGroup, schedule);
        }
        return false;
    }

    private checkIfTutorialGroupChanged(tutorialGroup: TutorialGroupDetailData, schedule?: TutorialGroupSchedule): boolean {
        const titleChanged = this.title() !== tutorialGroup.title;
        const tutorChanged = this.selectedTutorId() !== tutorialGroup.tutorId;
        const languageChanged = this.selectedLanguage() !== tutorialGroup.language;
        const modeChanged = (this.selectedMode() === Mode.OFFLINE && tutorialGroup.isOnline) || (this.selectedMode() === Mode.ONLINE && !tutorialGroup.isOnline);
        const campusChanged = this.campus() !== (tutorialGroup.campus ?? '');
        const capacityChanged = this.capacity() !== tutorialGroup.capacity;
        const additionalInformationChanged = this.additionalInformation() !== (tutorialGroup.additionalInformation ?? '');
        const tutorialGroupChanged = titleChanged || tutorChanged || languageChanged || modeChanged || campusChanged || capacityChanged || additionalInformationChanged;
        if (schedule) {
            const firstSessionStart = this.firstSessionStart();
            const firstSessionStartChanged = firstSessionStart ? firstSessionStart.getTime() !== dayjs(schedule.firstSessionStart).toDate().getTime() : true;
            const firstSessionEnd = this.firstSessionEnd();
            const firstSessionEndChanged = firstSessionEnd ? firstSessionEnd.getTime() !== dayjs(schedule.firstSessionEnd).toDate().getTime() : true;
            const repetitionFrequencyChanged = this.repetitionFrequency() !== schedule.repetitionFrequency;
            const tutorialPeriodEnd = this.tutorialPeriodEnd();
            const tutorialPeriodEndChanged = tutorialPeriodEnd ? tutorialPeriodEnd.getTime() !== dayjs(schedule.tutorialPeriodEnd).toDate().getTime() : true;
            const locationChanged = this.location() !== schedule.location;
            const scheduleChanged =
                !this.configureSessionPlan() || firstSessionStartChanged || firstSessionEndChanged || repetitionFrequencyChanged || tutorialPeriodEndChanged || locationChanged;
            return tutorialGroupChanged || scheduleChanged;
        }
        return tutorialGroupChanged || this.configureSessionPlan();
    }

    private computeIfInputsInvalid(): boolean {
        const titleInvalid = this.titleValidationResult().status === ValidationStatus.INVALID;
        const tutorInvalid = this.tutorValidationResult().status === ValidationStatus.INVALID;
        const languageInvalid = this.languageValidationResult().status === ValidationStatus.INVALID;
        const campusInvalid = this.campusValidationResult().status === ValidationStatus.INVALID;
        const additionalInformationInvalid = this.additionalInformationValidationResult().status === ValidationStatus.INVALID;
        const generalInformationInvalid = titleInvalid || tutorInvalid || languageInvalid || campusInvalid || additionalInformationInvalid;

        const firstSessionStartInvalid = this.firstSessionStartValidationResult().status === ValidationStatus.INVALID;
        const firstSessionEndInvalid = this.firstSessionEndValidationResult().status === ValidationStatus.INVALID;
        const tutorialPeriodEndInvalid = this.tutorialPeriodEndValidationResult().status === ValidationStatus.INVALID;
        const locationInvalid = this.locationValidationResult().status === ValidationStatus.INVALID;
        const scheduleInvalid = firstSessionStartInvalid || firstSessionEndInvalid || tutorialPeriodEndInvalid || locationInvalid;

        return generalInformationInvalid || (this.configureSessionPlan() && scheduleInvalid);
    }
}
