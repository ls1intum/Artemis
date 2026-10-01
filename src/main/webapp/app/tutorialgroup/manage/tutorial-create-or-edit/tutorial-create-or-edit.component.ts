import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TutorialGroupDetailData, TutorialGroupTutor } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialEditLanguagesInputComponent } from 'app/tutorialgroup/manage/tutorial-edit-languages-input/tutorial-edit-languages-input.component';
import dayjs from 'dayjs/esm';
import {
    TumAetUiButtonDirective,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiInputGroupComponent,
    TumAetUiInputNumberComponent,
    TumAetUiSelectComponent,
    TumAetUiToggleSwitchComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { Validation, ValidationStatus } from 'app/foundation/util/validation';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { CreateOrUpdateTutorialGroupRequest } from 'app/openapi/model/create-or-update-tutorial-group-request';
import { TutorialGroupSchedule } from 'app/openapi/model/tutorial-group-schedule';

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
        FormsModule,
        RouterLink,
        TumAetUiButtonDirective,
        TumAetUiDatePickerComponent,
        TumAetUiInputDirective,
        TumAetUiSelectComponent,
        TumAetUiTooltipDirective,
        TutorialEditLanguagesInputComponent,
        TumAetUiInputNumberComponent,
        TumAetUiInputGroupComponent,
        TumAetUiToggleSwitchComponent,
        TumAetUiConfirmDialogComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
    providers: [TumAetUiConfirmationService],
    templateUrl: './tutorial-create-or-edit.component.html',
    styleUrl: './tutorial-create-or-edit.component.scss',
})
export class TutorialCreateOrEditComponent {
    private confirmationService = inject(TumAetUiConfirmationService);
    private tutorialGroupApiService = inject(TutorialGroupApi);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    private readonly titleRegex = /^[A-Za-z0-9][A-Za-z0-9: -]*$/;
    protected readonly ValidationStatus = ValidationStatus;

    courseId = input.required<number>();
    tutors = input.required<TutorialGroupTutor[]>();
    tutorialGroup = input<TutorialGroupDetailData>();
    schedule = input<TutorialGroupSchedule>();

    title = signal('');
    titleValidationResult = computed<Validation>(() => this.computeTitleValidation());
    titleInputTouched = signal(false);
    selectedTutorId = signal<number | undefined>(undefined);
    tutorValidationResult = computed<Validation>(() => this.computeTutorValidation());
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

    // The session schedule is behind a toggle: off by default, and when on the schedule fields below are shown and
    // all four required.
    configureSessionPlan = signal(false);
    firstSessionStart = signal<dayjs.Dayjs | undefined>(undefined);
    firstSessionStartInputTouched = signal(false);
    firstSessionStartValidationResult = computed<Validation>(() => this.computeFirstSessionStartValidation());
    firstSessionEnd = signal<dayjs.Dayjs | undefined>(undefined);
    firstSessionEndInputTouched = signal(false);
    firstSessionEndValidationResult = computed<Validation>(() => this.computeFirstSessionEndValidation());
    repetitionFrequency = signal<number>(1);
    tutorialPeriodEnd = signal<dayjs.Dayjs | undefined>(undefined);
    tutorialPeriodEndInputTouched = signal(false);
    tutorialPeriodEndValidationResult = computed<Validation>(() => this.computeTeachingPeriodEndValidation());
    location = signal('');
    locationInputTouched = signal(false);
    locationValidationResult = computed<Validation>(() => this.computeLocationValidation());
    scheduleChangeOverwritesSessions = computed<boolean>(() => this.computeIfScheduleChangeOverwritesSessions());

    onUpdate = output<UpdateTutorialGroupEvent>();
    onCreate = output<CreateTutorialGroupEvent>();
    // What blocks saving, as a list of localized reasons shown on the disabled button - so it explains itself rather
    // than being an unexplained greyed-out control.
    saveDisabledReasons = computed<string[]>(() => this.computeSaveDisabledReasons());
    // The disabled button's tooltip needs the reasons as finished text, re-translated when the language changes.
    saveDisabledReasonTexts = computed<string[]>(() => {
        this.currentLocale();
        return this.saveDisabledReasons().map((key) => this.translateService.instant(key));
    });
    saveButtonDisabled = computed<boolean>(() => this.saveDisabledReasons().length > 0);
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
                this.firstSessionStart.set(dayjs(schedule.firstSessionStart));
                this.firstSessionEnd.set(dayjs(schedule.firstSessionEnd));
                this.repetitionFrequency.set(schedule.repetitionFrequency);
                this.tutorialPeriodEnd.set(dayjs(schedule.tutorialPeriodEnd));
                this.location.set(schedule.location);
                this.configureSessionPlan.set(true);
            }
        });
        effect(() => {
            this.tutorialGroupApiService.getUniqueLanguageValues(this.courseId()).subscribe({
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
        // aria-disabled keeps the button focusable so it can explain itself, which also leaves it clickable.
        if (this.saveButtonDisabled()) {
            return;
        }
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
            acceptSeverity: 'danger',
            rejectSeverity: 'secondary',
            accept: () => this.onUpdate.emit({ courseId, tutorialGroupId, updateTutorialGroupDTO: updateTutorialGroupRequest }),
        });
    }

    private assembleCreateOrUpdateTutorialGroupRequest(): CreateOrUpdateTutorialGroupRequest {
        const tutorialGroupSchedule: TutorialGroupSchedule | undefined = this.configureSessionPlan()
            ? {
                  firstSessionStart: this.firstSessionStart()!.format('YYYY-MM-DDTHH:mm:ss'),
                  firstSessionEnd: this.firstSessionEnd()!.format('YYYY-MM-DDTHH:mm:ss'),
                  repetitionFrequency: this.repetitionFrequency(),
                  tutorialPeriodEnd: this.tutorialPeriodEnd()!.format('YYYY-MM-DD'),
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
        if (firstSessionStart && !firstSessionEnd.isAfter(firstSessionStart)) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndNotAfterStart',
            };
        }
        if (firstSessionStart && !firstSessionStart.isSame(firstSessionEnd, 'day')) {
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
        if (firstSessionStart && !teachingPeriodEnd.isAfter(firstSessionStart)) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodNotAfterFirstSessionStart',
            };
        }
        if (firstSessionStart && teachingPeriodEnd.isAfter(firstSessionStart.add(2, 'year'))) {
            return {
                status: ValidationStatus.INVALID,
                message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodMoreThanTwoYearsAfterFirstSessionStart',
            };
        }
        const firstSessionEnd = this.firstSessionEnd();
        if (firstSessionEnd && !teachingPeriodEnd.isAfter(firstSessionEnd)) {
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
        if (this.configureSessionPlan()) {
            const firstSessionStartChanged = this.firstSessionStart()?.format('YYYY-MM-DDTHH:mm:ss') !== schedule.firstSessionStart;
            const firstSessionEndChanged = this.firstSessionEnd()?.format('YYYY-MM-DDTHH:mm:ss') !== schedule.firstSessionEnd;
            const repetitionFrequencyChanged = this.repetitionFrequency() !== schedule.repetitionFrequency;
            const tutorialPeriodEndChanged = this.tutorialPeriodEnd()?.format('YYYY-MM-DD') !== schedule.tutorialPeriodEnd;
            const locationChanged = this.location() !== schedule.location;
            return firstSessionStartChanged || firstSessionEndChanged || repetitionFrequencyChanged || tutorialPeriodEndChanged || locationChanged;
        }
        return true;
    }

    private computeSaveDisabledReasons(): string[] {
        const reasons: string[] = [];
        this.addReason(reasons, this.titleValidationResult());
        this.addReason(reasons, this.tutorValidationResult());
        this.addReason(reasons, this.languageValidationResult());
        this.addReason(reasons, this.campusValidationResult());
        this.addReason(reasons, this.additionalInformationValidationResult());
        if (this.configureSessionPlan()) {
            this.addReason(reasons, this.firstSessionStartValidationResult());
            this.addReason(reasons, this.firstSessionEndValidationResult());
            this.addReason(reasons, this.tutorialPeriodEndValidationResult());
            this.addReason(reasons, this.locationValidationResult());
        }
        // In edit mode a save that changes nothing has nothing to do; say so rather than leaving the button dead.
        const tutorialGroup = this.tutorialGroup();
        if (reasons.length === 0 && tutorialGroup && !this.checkIfTutorialGroupChanged(tutorialGroup, this.schedule())) {
            reasons.push('artemisApp.pages.createOrEditTutorialGroup.validationError.noChanges');
        }
        return reasons;
    }

    private addReason(reasons: string[], validation: Validation): void {
        if (validation.status === ValidationStatus.INVALID && validation.message) {
            reasons.push(validation.message);
        }
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
            const firstSessionStartChanged = this.firstSessionStart()?.valueOf() !== dayjs(schedule.firstSessionStart).valueOf();
            const firstSessionEndChanged = this.firstSessionEnd()?.valueOf() !== dayjs(schedule.firstSessionEnd).valueOf();
            const repetitionFrequencyChanged = this.repetitionFrequency() !== schedule.repetitionFrequency;
            const tutorialPeriodEndChanged = this.tutorialPeriodEnd()?.valueOf() !== dayjs(schedule.tutorialPeriodEnd).valueOf();
            const locationChanged = this.location() !== schedule.location;
            const scheduleChanged =
                !this.configureSessionPlan() || firstSessionStartChanged || firstSessionEndChanged || repetitionFrequencyChanged || tutorialPeriodEndChanged || locationChanged;
            return tutorialGroupChanged || scheduleChanged;
        }
        return tutorialGroupChanged || this.configureSessionPlan();
    }
}
