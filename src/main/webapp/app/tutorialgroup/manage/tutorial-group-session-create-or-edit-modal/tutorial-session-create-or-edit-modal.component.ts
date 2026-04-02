import { Component, computed, inject, output, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { Validation, ValidationStatus } from 'app/shared/util/validation';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { TooltipModule } from 'primeng/tooltip';
import dayjs from 'dayjs/esm';
import { InputNumberModule } from 'primeng/inputnumber';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { CreateOrUpdateTutorialGroupSessionRequest } from 'app/openapi/model/createOrUpdateTutorialGroupSessionRequest';

export interface UpdateTutorialGroupSessionData {
    tutorialGroupSessionId: number;
    updateTutorialGroupSessionRequest: CreateOrUpdateTutorialGroupSessionRequest;
}

@Component({
    selector: 'jhi-tutorial-session-create-or-edit-modal',
    imports: [
        DialogModule,
        FormsModule,
        DatePickerModule,
        InputGroupModule,
        InputTextModule,
        ButtonModule,
        InputGroupAddonModule,
        TooltipModule,
        InputNumberModule,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    templateUrl: './tutorial-session-create-or-edit-modal.component.html',
    styleUrl: './tutorial-session-create-or-edit-modal.component.scss',
})
export class TutorialSessionCreateOrEditModalComponent {
    protected readonly ValidationStatus = ValidationStatus;

    private translateService = inject(TranslateService);
    private session = signal<TutorialGroupSession | undefined>(undefined);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private inputsInvalid = computed(() => this.computeIfInputsInvalid());

    isOpen = signal(false);
    date = signal<Date | null>(null);
    dateValidationResult = computed<Validation>(() => this.computeDateValidation());
    dateInputTouched = signal(false);
    startTime = signal<Date | null>(null);
    startTimeValidationResult = computed<Validation>(() => this.computeStartTimeValidation());
    startTimeInputTouched = signal(false);
    endTime = signal<Date | null>(null);
    endTimeValidationResult = computed<Validation>(() => this.computeEndTimeValidation());
    endTimeInputTouched = signal(false);
    location = signal<string>('');
    locationValidationResult = computed<Validation>(() => this.computeLocationValidation());
    locationInputTouched = signal(false);
    saveButtonDisabled = computed<boolean>(() => this.computeIfSaveButtonDisabled());
    attendance = signal<number | null>(null);
    header = computed(() => this.computeHeader());
    onUpdate = output<UpdateTutorialGroupSessionData>();
    onCreate = output<CreateOrUpdateTutorialGroupSessionRequest>();

    open(session?: TutorialGroupSession) {
        if (session) {
            this.session.set(session);
            this.date.set(session.start.toDate());
            this.startTime.set(session.start.toDate());
            this.endTime.set(session.end.toDate());
            this.location.set(session.location);
            this.attendance.set(session.attendance ?? null);
        }
        this.isOpen.set(true);
    }

    save() {
        const session = this.session();
        if (session) {
            this.updateSession(session);
        } else {
            this.createSession();
        }
        this.clearData();
        this.isOpen.set(false);
    }

    cancel() {
        this.clearData();
        this.isOpen.set(false);
    }

    clearData() {
        this.session.set(undefined);
        this.date.set(null);
        this.dateInputTouched.set(false);
        this.startTime.set(null);
        this.startTimeInputTouched.set(false);
        this.endTime.set(null);
        this.endTimeInputTouched.set(false);
        this.location.set('');
        this.locationInputTouched.set(false);
        this.attendance.set(null);
    }

    private createSession() {
        const createTutorialGroupSessionRequest = this.constructCreateOrUpdateTutorialGroupSessionRequest();
        this.onCreate.emit(createTutorialGroupSessionRequest);
    }

    private updateSession(session: TutorialGroupSession) {
        const tutorialGroupSessionId = session.id;
        const updateTutorialGroupSessionRequest = this.constructCreateOrUpdateTutorialGroupSessionRequest();
        const updateTutorialGroupSessionData: UpdateTutorialGroupSessionData = {
            tutorialGroupSessionId: tutorialGroupSessionId,
            updateTutorialGroupSessionRequest: updateTutorialGroupSessionRequest,
        };
        this.onUpdate.emit(updateTutorialGroupSessionData);
    }

    private constructCreateOrUpdateTutorialGroupSessionRequest(): CreateOrUpdateTutorialGroupSessionRequest {
        return {
            date: dayjs(this.date()).format('YYYY-MM-DD'),
            startTime: dayjs(this.startTime()).format('HH:mm'),
            endTime: dayjs(this.endTime()).format('HH:mm'),
            location: this.location(),
            attendance: this.attendance() ?? undefined,
        };
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
        if (date === null) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.dateRequired',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeStartTimeValidation(): Validation {
        const startTime = this.startTime();
        if (startTime === null) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.startTimeRequired',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeEndTimeValidation(): Validation {
        const endTime = this.endTime();
        if (endTime === null) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.endTimeRequired',
            };
        }
        const startTime = this.startTime();
        if (startTime) {
            const startMinutes = startTime.getHours() * 60 + startTime.getMinutes();
            const endMinutes = endTime.getHours() * 60 + endTime.getMinutes();
            if (endMinutes <= startMinutes) {
                return {
                    status: ValidationStatus.INVALID,
                    message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.endTimeNotAfterStartTime',
                };
            }
        }
        return { status: ValidationStatus.VALID };
    }

    private computeLocationValidation(): Validation {
        const trimmedLocation = this.location().trim();
        if (trimmedLocation === '') {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.locationRequired',
            };
        }
        if (trimmedLocation.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.locationLength',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeIfInputsInvalid() {
        const dateInvalid = this.dateValidationResult().status === ValidationStatus.INVALID;
        const startTimeInvalid = this.startTimeValidationResult().status === ValidationStatus.INVALID;
        const endTimeInvalid = this.endTimeValidationResult().status === ValidationStatus.INVALID;
        const locationInvalid = this.locationValidationResult().status === ValidationStatus.INVALID;
        return dateInvalid || startTimeInvalid || endTimeInvalid || locationInvalid;
    }

    private checkIfSessionChanged(session: TutorialGroupSession): boolean {
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
        const attendanceChanged = (this.attendance() ?? undefined) !== session.attendance;
        return dateChanged || startTimeChanged || endTimeChanged || locationChanged || attendanceChanged;
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant(`artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.header.${this.session() ? 'edit' : 'create'}`);
    }
}
