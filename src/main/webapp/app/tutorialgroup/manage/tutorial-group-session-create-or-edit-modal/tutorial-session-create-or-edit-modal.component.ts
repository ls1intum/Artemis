import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiDatePickerComponent,
    TumAetUiDialogComponent,
    TumAetUiInputDirective,
    TumAetUiInputGroupAddonComponent,
    TumAetUiInputGroupComponent,
    TumAetUiInputNumberComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { Validation, ValidationStatus } from 'app/foundation/util/validation';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { CreateOrUpdateTutorialGroupSessionRequest } from 'app/openapi/model/create-or-update-tutorial-group-session-request';

export interface UpdateTutorialGroupSessionData {
    tutorialGroupSessionId: number;
    updateTutorialGroupSessionRequest: CreateOrUpdateTutorialGroupSessionRequest;
}

@Component({
    selector: 'jhi-tutorial-session-create-or-edit-modal',
    imports: [
        FormsModule,
        // Contained PrimeNG fallback: the date and the two time inputs have no TUM AET UI equivalent yet.
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiDatePickerComponent,
        TumAetUiDialogComponent,
        TumAetUiInputDirective,
        TumAetUiInputGroupAddonComponent,
        TumAetUiInputGroupComponent,
        TumAetUiInputNumberComponent,
        TumAetUiTooltipDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    templateUrl: './tutorial-session-create-or-edit-modal.component.html',
    styleUrl: './tutorial-session-create-or-edit-modal.component.scss',
})
export class TutorialSessionCreateOrEditModalComponent {
    private translateService = inject(TranslateService);

    protected readonly ValidationStatus = ValidationStatus;
    protected readonly faCircleInfo = faCircleInfo;

    private session = signal<TutorialGroupSession | undefined>(undefined);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private inputsInvalid = computed(() => this.computeIfInputsInvalid());

    isOpen = signal(false);
    date = signal<dayjs.Dayjs | undefined>(undefined);
    dateValidationResult = computed<Validation>(() => this.computeDateValidation());
    dateInputTouched = signal(false);
    // The date field is typed; a picker keeps its last committed value when the text turns invalid, so a save is
    // blocked while it shows text that does not parse - otherwise it would send the stale value behind it. The
    // time fields are inline steppers with no text to be invalid.
    dateTextValid = signal(true);
    startTime = signal<dayjs.Dayjs | undefined>(undefined);
    startTimeValidationResult = computed<Validation>(() => this.computeStartTimeValidation());
    startTimeInputTouched = signal(false);
    endTime = signal<dayjs.Dayjs | undefined>(undefined);
    endTimeValidationResult = computed<Validation>(() => this.computeEndTimeValidation());
    endTimeInputTouched = signal(false);
    location = signal<string>('');
    locationValidationResult = computed<Validation>(() => this.computeLocationValidation());
    locationInputTouched = signal(false);
    saveButtonDisabled = computed<boolean>(() => this.computeIfSaveButtonDisabled());
    attendance = signal<number | null>(null);
    header = computed(() => this.computeHeader());
    // Match the action verb to the mode - and to the "Create Session" / "Edit Session" header.
    primaryActionLabel = computed(() => (this.session() ? 'entity.action.save' : 'entity.action.create'));
    onUpdate = output<UpdateTutorialGroupSessionData>();
    onCreate = output<CreateOrUpdateTutorialGroupSessionRequest>();

    open(session?: TutorialGroupSession) {
        if (session) {
            this.session.set(session);
            this.date.set(session.start);
            this.startTime.set(session.start);
            this.endTime.set(session.end);
            this.location.set(session.location);
            this.attendance.set(session.attendance ?? null);
        }
        this.isOpen.set(true);
    }

    // The picker has no blur event, so a committed change is what marks the field touched - which is what gates the
    // required message, exactly as the old blur did.
    onDateChange(value: dayjs.Dayjs | undefined) {
        this.date.set(value);
        this.dateInputTouched.set(true);
    }

    onStartTimeChange(value: dayjs.Dayjs | undefined) {
        this.startTime.set(value);
        this.startTimeInputTouched.set(true);
    }

    onEndTimeChange(value: dayjs.Dayjs | undefined) {
        this.endTime.set(value);
        this.endTimeInputTouched.set(true);
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
        this.date.set(undefined);
        this.dateInputTouched.set(false);
        this.dateTextValid.set(true);
        this.startTime.set(undefined);
        this.startTimeInputTouched.set(false);
        this.endTime.set(undefined);
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
            date: this.date()!.format('YYYY-MM-DD'),
            startTime: this.startTime()!.format('HH:mm'),
            endTime: this.endTime()!.format('HH:mm'),
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
        if (date === undefined) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.dateRequired',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeStartTimeValidation(): Validation {
        const startTime = this.startTime();
        if (startTime === undefined) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.startTimeRequired',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeEndTimeValidation(): Validation {
        const endTime = this.endTime();
        if (endTime === undefined) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.endTimeRequired',
            };
        }
        const startTime = this.startTime();
        if (startTime) {
            const startMinutes = startTime.hour() * 60 + startTime.minute();
            const endMinutes = endTime.hour() * 60 + endTime.minute();
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
        return dateInvalid || startTimeInvalid || endTimeInvalid || locationInvalid || !this.dateTextValid();
    }

    private checkIfSessionChanged(session: TutorialGroupSession): boolean {
        const date = this.date();
        const startTime = this.startTime();
        const endTime = this.endTime();
        const location = this.location().trim();
        if (!date || !startTime || !endTime) return false;

        const originalStart = session.start;
        const originalEnd = session.end;
        const dateChanged = date.year() !== originalStart.year() || date.month() !== originalStart.month() || date.date() !== originalStart.date();
        const startTimeChanged = startTime.hour() !== originalStart.hour() || startTime.minute() !== originalStart.minute();
        const endTimeChanged = endTime.hour() !== originalEnd.hour() || endTime.minute() !== originalEnd.minute();
        const locationChanged = location !== session.location;
        const attendanceChanged = (this.attendance() ?? undefined) !== session.attendance;
        return dateChanged || startTimeChanged || endTimeChanged || locationChanged || attendanceChanged;
    }

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant(`artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.header.${this.session() ? 'edit' : 'create'}`);
    }
}
