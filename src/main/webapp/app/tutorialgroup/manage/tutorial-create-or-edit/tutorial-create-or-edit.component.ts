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

type Mode = {
    name: string;
};

export enum TutorialCreateOrEditValidationStatus {
    VALID = 'VALID',
    INVALID = 'INVALID',
}

export type TutorialCreateOrEditValidation = { status: TutorialCreateOrEditValidationStatus.INVALID; message: string } | { status: TutorialCreateOrEditValidationStatus.VALID };

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
    ],
    providers: [ConfirmationService],
    templateUrl: './tutorial-create-or-edit.component.html',
    styleUrl: './tutorial-create-or-edit.component.scss',
})
export class TutorialCreateOrEditComponent {
    private readonly titleRegex = /^[A-Za-z0-9][A-Za-z0-9: -]*$/;
    protected readonly TutorialEditValidationStatus = TutorialCreateOrEditValidationStatus;
    private confirmationService = inject(ConfirmationService);

    courseId = input.required<number>();
    tutorialGroupId = input<number>();
    tutors = input.required<TutorialGroupTutorDTO[]>();
    tutorialGroup = input<TutorialGroupDTO>();
    schedule = input<TutorialGroupScheduleDTO>();

    title = signal('');
    titleValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeTitleValidation());
    titleInputTouched = signal(false);
    selectedTutorId = signal<number | undefined>(undefined);
    tutorValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeTutorValidation());
    tutorInputTouched = signal(false);
    language = signal<string>('');
    modes: Mode[] = [{ name: 'Online' }, { name: 'Offline' }];
    selectedMode = signal<Mode>({ name: 'Online' });
    campus = signal('');
    campusValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeCampusValidation());
    capacity = signal<number | undefined>(undefined);
    additionalInformation = signal('');

    configureSessionPlan = signal(true);
    firstSessionStart = signal<Date | undefined>(undefined);
    firstSessionStartInputTouched = signal(false);
    firstSessionStartValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeFirstSessionStartValidation());
    firstSessionEnd = signal<Date | undefined>(undefined);
    firstSessionEndInputTouched = signal(false);
    firstSessionEndValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeFirstSessionEndValidation());
    repetitionFrequency = signal<number>(1);
    tutorialPeriodEnd = signal<Date | undefined>(undefined);
    tutorialPeriodEndInputTouched = signal(false);
    tutorialPeriodEndValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeTeachingPeriodEndValidation());
    location = signal('');
    locationInputTouched = signal(false);
    locationValidationResult = computed<TutorialCreateOrEditValidation>(() => this.computeLocationValidation());
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
            header: 'Confirm Save',
            message: 'You made changes to the schedule. If you save, this will overwrite all existing sessions.',
            acceptLabel: 'Save and Overwrite',
            rejectLabel: 'Cancel',
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

    private computeTitleValidation(): TutorialCreateOrEditValidation {
        const title = this.title();
        if (!title.match(this.titleRegex)) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'Title must start with a letter or digit, and can otherwise only contain letters, digits, colons, spaces and dashes.',
            };
        }
        const trimmedTitle = title.trim();
        if (trimmedTitle.length > 19) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'Title must contain at most 19 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return {
            status: TutorialCreateOrEditValidationStatus.VALID,
        };
    }

    private computeCampusValidation(): TutorialCreateOrEditValidation {
        const trimmedCampus = this.campus().trim();
        if (trimmedCampus && trimmedCampus.length > 255) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'Campus must contain at most 255 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return { status: TutorialCreateOrEditValidationStatus.VALID };
    }

    private computeFirstSessionStartValidation(): TutorialCreateOrEditValidation {
        return this.firstSessionStart()
            ? { status: TutorialCreateOrEditValidationStatus.VALID }
            : {
                  status: TutorialCreateOrEditValidationStatus.INVALID,
                  message: 'Please choose a date.',
              };
    }

    private computeFirstSessionEndValidation(): TutorialCreateOrEditValidation {
        const firstSessionEnd = this.firstSessionEnd();
        if (!firstSessionEnd) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && firstSessionEnd <= firstSessionStart) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'The end of the first session must be after its start.',
            };
        }
        if (
            firstSessionStart &&
            (firstSessionStart.getFullYear() !== firstSessionEnd.getFullYear() ||
                firstSessionStart.getMonth() !== firstSessionEnd.getMonth() ||
                firstSessionStart.getDate() !== firstSessionEnd.getDate())
        ) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'The end of the first session must be on the same day as its start.',
            };
        }
        return { status: TutorialCreateOrEditValidationStatus.VALID };
    }

    private computeTeachingPeriodEndValidation(): TutorialCreateOrEditValidation {
        const teachingPeriodEnd = this.tutorialPeriodEnd();
        if (!teachingPeriodEnd) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && teachingPeriodEnd <= firstSessionStart) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: "The end of the teaching period must be after the first session's start.",
            };
        }
        const firstSessionEnd = this.firstSessionEnd();
        if (firstSessionEnd && teachingPeriodEnd <= firstSessionEnd) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: "The end of the teaching period must be after the first session's end.",
            };
        }
        return { status: TutorialCreateOrEditValidationStatus.VALID };
    }

    private computeTutorValidation(): TutorialCreateOrEditValidation {
        const selectedTutorId = this.selectedTutorId();
        if (selectedTutorId) return { status: TutorialCreateOrEditValidationStatus.VALID };
        return {
            status: TutorialCreateOrEditValidationStatus.INVALID,
            message: 'Please choose a tutor.',
        };
    }

    private computeLocationValidation(): TutorialCreateOrEditValidation {
        const location = this.location();
        if (!location) {
            return {
                status: TutorialCreateOrEditValidationStatus.INVALID,
                message: 'Please choose a location.',
            };
        }
        return { status: TutorialCreateOrEditValidationStatus.VALID };
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
        const titleInvalid = this.titleValidationResult().status === TutorialCreateOrEditValidationStatus.INVALID;
        const tutorInvalid = this.tutorValidationResult().status === TutorialCreateOrEditValidationStatus.INVALID;
        const generalInformationInvalid = titleInvalid || tutorInvalid;

        const firstSessionStartInvalid = this.firstSessionStartValidationResult().status === TutorialCreateOrEditValidationStatus.INVALID;
        const firstSessionEndInvalid = this.firstSessionEndValidationResult().status === TutorialCreateOrEditValidationStatus.INVALID;
        const tutorialPeriodEndInvalid = this.tutorialPeriodEndValidationResult().status === TutorialCreateOrEditValidationStatus.INVALID;
        const locationInvalid = this.locationValidationResult().status === TutorialCreateOrEditValidationStatus.INVALID;
        const scheduleInvalid = firstSessionStartInvalid || firstSessionEndInvalid || tutorialPeriodEndInvalid || locationInvalid;

        return generalInformationInvalid || (this.configureSessionPlan() && scheduleInvalid);
    }
}
