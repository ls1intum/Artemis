import { Component, computed, output, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { Validation, ValidationStatus } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { TooltipModule } from 'primeng/tooltip';
import { UpdateTutorialGroupSessionDTO } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import dayjs from 'dayjs/esm';

export interface UpdateTutorialGroupSessionData {
    tutorialGroupSessionId: number;
    updateTutorialGroupSessionDTO: UpdateTutorialGroupSessionDTO;
}

@Component({
    selector: 'jhi-tutorial-session-create-or-edit-modal',
    imports: [DialogModule, FormsModule, DatePickerModule, InputGroupModule, InputTextModule, ButtonModule, InputGroupAddonModule, TooltipModule],
    templateUrl: './tutorial-session-create-or-edit-modal.component.html',
    styleUrl: './tutorial-session-create-or-edit-modal.component.scss',
})
export class TutorialSessionCreateOrEditModalComponent {
    protected readonly ValidationStatus = ValidationStatus;

    private session = signal<TutorialGroupSessionDTO | undefined>(undefined);

    isOpen = signal(false);

    date = signal<Date | undefined>(undefined);
    dateValidationResult = computed<Validation>(() => this.computeDateValidation());
    dateInputTouched = signal(false);

    startTime = signal<Date | undefined>(undefined);
    startTimeValidationResult = computed<Validation>(() => this.computeStartTimeValidation());
    startTimeInputTouched = signal(false);

    endTime = signal<Date | undefined>(undefined);
    endTimeValidationResult = computed<Validation>(() => this.computeEndTimeValidation());
    endTimeInputTouched = signal(false);

    location = signal<string>('');
    locationValidationResult = computed<Validation>(() => this.computeLocationValidation());
    locationInputTouched = signal(false);

    inputsInvalid = computed(() => this.computeIfInputsInvalid());
    saveButtonDisabled = computed<boolean>(() => this.computeIfSaveButtonDisabled());

    onUpdate = output<UpdateTutorialGroupSessionData>();

    open(session?: TutorialGroupSessionDTO) {
        if (session) {
            this.session.set(session);
            this.date.set(session.start.toDate());
            this.startTime.set(session.start.toDate());
            this.endTime.set(session.end.toDate());
            this.location.set(session.location);
        }
        this.isOpen.set(true);
    }

    save() {
        const session = this.session();
        if (session) {
            this.updateSession(session);
        } else {
            // TODO: implement create
        }
        this.clearData();
        this.isOpen.set(false);
    }

    cancel() {
        this.clearData();
        this.isOpen.set(false);
    }

    private updateSession(session: TutorialGroupSessionDTO) {
        const tutorialGroupSessionId = session.id;
        const date = dayjs(this.date()).format('YYYY-MM-DD');
        const startTime = dayjs(this.startTime()).format('HH:mm');
        const endTime = dayjs(this.endTime()).format('HH:mm');
        const location = this.location();
        const updateTutorialGroupSessionDTO: UpdateTutorialGroupSessionDTO = {
            date: date,
            startTime: startTime,
            endTime: endTime,
            location: location,
        };
        const updateTutorialGroupSessionData: UpdateTutorialGroupSessionData = {
            tutorialGroupSessionId: tutorialGroupSessionId,
            updateTutorialGroupSessionDTO: updateTutorialGroupSessionDTO,
        };
        this.onUpdate.emit(updateTutorialGroupSessionData);
    }

    private clearData() {
        this.session.set(undefined);
        this.date.set(undefined);
        this.dateInputTouched.set(false);
        this.startTime.set(undefined);
        this.startTimeInputTouched.set(false);
        this.endTime.set(undefined);
        this.endTimeInputTouched.set(false);
        this.location.set('');
        this.locationInputTouched.set(false);
    }

    private computeIfSaveButtonDisabled(): boolean {
        const inputsInvalid = this.inputsInvalid();
        if (inputsInvalid) return true;
        const session = this.session();
        if (session) {
            return !this.checkIfSessionChanged(session);
        }
        return false;
    }

    private computeDateValidation(): Validation {
        const date = this.date();
        if (date === undefined) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeStartTimeValidation(): Validation {
        const startTime = this.startTime();
        if (startTime === undefined) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose a start time.',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeEndTimeValidation(): Validation {
        const endTime = this.endTime();
        if (endTime === undefined) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose an end time.',
            };
        }
        const startTime = this.startTime();
        if (startTime) {
            const startMinutes = startTime.getHours() * 60 + startTime.getMinutes();
            const endMinutes = endTime.getHours() * 60 + endTime.getMinutes();
            if (endMinutes <= startMinutes) {
                return { status: ValidationStatus.INVALID, message: 'End time must be after the start time.' };
            }
        }
        return { status: ValidationStatus.VALID };
    }

    private computeLocationValidation(): Validation {
        const trimmedLocation = this.location().trim();
        if (trimmedLocation === '') {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose a location.',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeIfInputsInvalid() {
        const dateInvalid = this.computeDateValidation().status === ValidationStatus.INVALID;
        const startTimeInvalid = this.computeStartTimeValidation().status === ValidationStatus.INVALID;
        const endTimeInvalid = this.computeEndTimeValidation().status === ValidationStatus.INVALID;
        const locationInvalid = this.computeLocationValidation().status === ValidationStatus.INVALID;
        return dateInvalid || startTimeInvalid || endTimeInvalid || locationInvalid;
    }

    private checkIfSessionChanged(session: TutorialGroupSessionDTO): boolean {
        const date = this.date();
        const startTime = this.startTime();
        const endTime = this.endTime();
        const location = this.location().trim();
        if (!date || !startTime || !endTime) return false;

        const originalStart = session.start;
        const originalEnd = session.end;
        const dateChanged = date.getFullYear() !== originalStart.year() || date.getMonth() !== originalStart.month() || date.getDate() !== originalStart.date();
        const startTimeChanged = startTime.getHours() !== originalStart.hour() || startTime.getMinutes() !== originalStart.minute();
        const endTimeChanged = endTime.getHours() !== originalEnd.hour() || endTime.getMinutes() !== originalEnd.minute();
        const locationChanged = location !== session.location;
        return dateChanged || startTimeChanged || endTimeChanged || locationChanged;
    }
}
