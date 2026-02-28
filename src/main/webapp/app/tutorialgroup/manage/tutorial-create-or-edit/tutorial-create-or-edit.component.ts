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
import { CreateOrUpdateTutorialGroupDTO, TutorialGroupDTO, TutorialGroupScheduleDTO, TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialEditLanguagesInputComponent } from 'app/tutorialgroup/manage/tutorial-edit-languages-input/tutorial-edit-languages-input.component';
import dayjs from 'dayjs/esm';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';

type Mode = {
    name: string;
};

export enum ValidationStatus {
    VALID = 'VALID',
    INVALID = 'INVALID',
}

export type Validation = { status: ValidationStatus.INVALID; message: string } | { status: ValidationStatus.VALID };

export interface CreateTutorialGroupEvent {
    courseId: number;
    createTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO;
}

export interface UpdateTutorialGroupEvent {
    courseId: number;
    tutorialGroupId: number;
    updateTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO;
}

// TODO: add warning when changing schedule (all existing sessions will be overwritten)
// TODO: add input to capture updateChannelName flag for edit mode
// TODO: only enable save on edit if any field changed
// TODO: decide whether we want to support notification in edit
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
    private translateService = inject(TranslateService);

    courseId = input.required<number>();
    tutorialGroupId = input<number>();
    tutors = input.required<TutorialGroupTutorDTO[]>();
    tutorialGroup = input<TutorialGroupDTO>();
    schedule = input<TutorialGroupScheduleDTO>();

    title = signal('');
    titleValidationResult = computed<Validation>(() => this.computeTitleValidation());
    titleInputTouched = signal(false);
    selectedTutorId = signal<number | undefined>(undefined);
    tutorValidationResult = computed<Validation>(() => this.computeTutorValidation());
    tutorInputTouched = signal(false);
    language = signal<string>('');
    modes: Mode[] = [{ name: 'Online' }, { name: 'Offline' }];
    selectedMode = signal<Mode>({ name: 'Online' });
    campus = signal('');
    campusValidationResult = computed<Validation>(() => this.computeCampusValidation());
    capacity = signal<number | undefined>(undefined);
    additionalInformation = signal('');

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
                this.language.set(tutorialGroup.language);
                this.selectedMode.set(tutorialGroup.isOnline ? { name: 'Online' } : { name: 'Offline' });
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
    }

    save() {
        const courseId = this.courseId();
        if (this.tutorialGroup()) {
            const tutorialGroupId = this.tutorialGroupId();
            if (!tutorialGroupId) return;
            const updateTutorialGroupDTO = this.assembleCreateOrUpdateTutorialGroupDTO();
            if (this.scheduleChangeOverwritesSessions()) {
                this.confirmScheduleChangingSave(courseId, tutorialGroupId, updateTutorialGroupDTO);
            } else {
                this.onUpdate.emit({ courseId: courseId, tutorialGroupId: tutorialGroupId, updateTutorialGroupDTO: updateTutorialGroupDTO });
            }
        } else {
            const createTutorialGroupDTO = this.assembleCreateOrUpdateTutorialGroupDTO();
            this.onCreate.emit({ courseId: courseId, createTutorialGroupDTO: createTutorialGroupDTO });
        }
    }

    private confirmScheduleChangingSave(courseId: number, tutorialGroupId: number, updateTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO) {
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.pages.createOrEditTutorialGroup.confirmSaveDialog.header'),
            message: this.translateService.instant('artemisApp.pages.createOrEditTutorialGroup.confirmSaveDialog.message'),
            acceptLabel: this.translateService.instant('artemisApp.pages.createOrEditTutorialGroup.confirmSaveDialog.acceptButtonLabel'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptButtonStyleClass: 'p-button-danger',
            rejectButtonStyleClass: 'p-button-secondary',
            accept: () => this.onUpdate.emit({ courseId, tutorialGroupId, updateTutorialGroupDTO }),
        });
    }

    private assembleCreateOrUpdateTutorialGroupDTO(): CreateOrUpdateTutorialGroupDTO {
        const tutorialGroupScheduleDTO: TutorialGroupScheduleDTO | undefined = this.configureSessionPlan()
            ? {
                  firstSessionStart: dayjs(this.firstSessionStart()).format('YYYY-MM-DDTHH:mm:ss'),
                  firstSessionEnd: dayjs(this.firstSessionEnd()).format('YYYY-MM-DDTHH:mm:ss'),
                  repetitionFrequency: this.repetitionFrequency(),
                  tutorialPeriodEnd: dayjs(this.tutorialPeriodEnd()).format('YYYY-MM-DD'),
                  location: this.location(),
              }
            : undefined;
        return {
            title: this.title(),
            tutorId: this.selectedTutorId()!,
            language: this.language(),
            isOnline: this.selectedMode().name === 'Online',
            campus: this.campus() || undefined,
            capacity: this.capacity(),
            additionalInformation: this.additionalInformation() || undefined,
            tutorialGroupScheduleDTO: tutorialGroupScheduleDTO,
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
        const firstSessionEnd = this.firstSessionEnd();
        if (firstSessionEnd && teachingPeriodEnd <= firstSessionEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodNotAfterFirstSessionEnd',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeTutorValidation(): Validation {
        const selectedTutorId = this.selectedTutorId();
        if (selectedTutorId) return { status: ValidationStatus.VALID };
        return {
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.tutorRequired',
        };
    }

    private computeLocationValidation(): Validation {
        const location = this.location();
        if (!location) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.locationRequired',
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
        const titleInvalid = this.titleValidationResult().status === ValidationStatus.INVALID;
        const tutorInvalid = this.tutorValidationResult().status === ValidationStatus.INVALID;
        const generalInformationInvalid = titleInvalid || tutorInvalid;

        const firstSessionStartInvalid = this.firstSessionStartValidationResult().status === ValidationStatus.INVALID;
        const firstSessionEndInvalid = this.firstSessionEndValidationResult().status === ValidationStatus.INVALID;
        const tutorialPeriodEndInvalid = this.tutorialPeriodEndValidationResult().status === ValidationStatus.INVALID;
        const locationInvalid = this.locationValidationResult().status === ValidationStatus.INVALID;
        const scheduleInvalid = firstSessionStartInvalid || firstSessionEndInvalid || tutorialPeriodEndInvalid || locationInvalid;

        return generalInformationInvalid || (this.configureSessionPlan() && scheduleInvalid);
    }
}
