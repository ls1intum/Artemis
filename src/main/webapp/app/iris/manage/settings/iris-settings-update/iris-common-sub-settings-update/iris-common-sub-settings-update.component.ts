import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { IrisEventType, IrisSubSettings, IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisVariant } from 'app/entities/iris/settings/iris-variant';
import { AccountService } from 'app/core/auth/account.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faCircleExclamation, faQuestionCircle, faTrash } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercise/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-iris-common-sub-settings-update',
    templateUrl: './iris-common-sub-settings-update.component.html',
    imports: [
        TranslateDirective,
        NgClass,
        FormsModule,
        FaIconComponent,
        NgbTooltip,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        ArtemisTranslatePipe,
    ],
})
export class IrisCommonSubSettingsUpdateComponent implements OnInit, OnChanges {
    private irisSettingsService = inject(IrisSettingsService);
    private courseManagementService = inject(CourseManagementService);
    private exerciseService = inject(ExerciseService);
    private alertService = inject(AlertService);

    @Input()
    subSettings?: IrisSubSettings;

    @Input()
    parentSubSettings?: IrisSubSettings;

    @Input()
    settingsType: IrisSettingsType;

    @Input()
    courseId?: number;

    @Output()
    onChanges = new EventEmitter<IrisSubSettings>();

    isAdmin: boolean;

    inheritAllowedVariants: boolean;

    eventInParentDisabledStatusMap = new Map<IrisEventType, boolean | undefined>();

    availableVariants: IrisVariant[] = [];

    allowedVariants: IrisVariant[] = [];

    enabled: boolean;

    categories: string[] = [];

    exerciseChatEvents: IrisEventType[] = [IrisEventType.BUILD_FAILED, IrisEventType.PROGRESS_STALLED];

    // Settings types
    EXERCISE = IrisSettingsType.EXERCISE;
    COURSE = IrisSettingsType.COURSE;
    TEXT_EXERCISE_CHAT = IrisSubSettingsType.TEXT_EXERCISE_CHAT;
    CHAT = IrisSubSettingsType.CHAT;
    // Button types
    WARNING = ButtonType.WARNING;
    // Icons
    readonly faTrash = faTrash;
    readonly faQuestionCircle = faQuestionCircle;
    readonly faCircleExclamation = faCircleExclamation;

    protected readonly IrisSubSettings = IrisSubSettings;
    protected readonly IrisSubSettingsType = IrisSubSettingsType;

    protected readonly eventTranslationKeys = {
        [IrisEventType.BUILD_FAILED]: 'artemisApp.iris.settings.subSettings.proactivityBuildFailedEventEnabled.label',
        [IrisEventType.PROGRESS_STALLED]: 'artemisApp.iris.settings.subSettings.proactivityProgressStalledEventEnabled.label',
    };

    constructor() {
        const accountService = inject(AccountService);

        this.isAdmin = accountService.isAdmin();
    }

    ngOnInit() {
        this.enabled = this.subSettings?.enabled ?? false;
        this.loadCategories();
        this.loadVariants();
        this.inheritAllowedVariants = !!(!this.subSettings?.allowedVariants && this.parentSubSettings);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (!this.inheritAllowedVariants && changes.availableVariants) {
            this.allowedVariants = this.getAllowedVariants();
        }
        if (changes.subSettings) {
            this.enabled = this.subSettings?.enabled ?? false;
        }
        if (changes.parentSubSettings || changes.subSettings) {
            this.updateEventDisabledStatus();
        }
    }

    loadCategories() {
        if (this.settingsType === this.COURSE) {
            this.courseManagementService.findAllCategoriesOfCourse(this.courseId!).subscribe({
                next: (response: HttpResponse<string[]>) => {
                    this.categories = this.exerciseService
                        .convertExerciseCategoriesAsStringFromServer(response.body!)
                        .map((category) => category.category)
                        .filter((category) => category !== undefined)
                        .map((category) => category!);
                    // Remove duplicate categories
                    this.categories = Array.from(new Set(this.categories));
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    loadVariants(): void {
        if (!this.subSettings?.type) {
            return;
        }
        this.irisSettingsService.getVariantsForFeature(this.subSettings?.type).subscribe((variants) => {
            this.availableVariants = variants ?? this.availableVariants;
            this.allowedVariants = this.getAllowedVariants();
        });
    }

    getAllowedVariants(): IrisVariant[] {
        return this.availableVariants.filter((variant) => (this.subSettings?.allowedVariants ?? this.parentSubSettings?.allowedVariants ?? []).includes(variant.id));
    }

    getSelectedVariantName(): string | undefined {
        return this.availableVariants.find((variant) => variant.id === this.subSettings?.selectedVariant)?.name ?? this.subSettings?.selectedVariant;
    }

    getSelectedVariantNameParent(): string | undefined {
        return this.availableVariants.find((variant) => variant.id === this.parentSubSettings?.selectedVariant)?.name ?? this.parentSubSettings?.selectedVariant;
    }

    onAllowedIrisVariantsSelectionChange(variant: IrisVariant) {
        this.inheritAllowedVariants = false;
        if (this.allowedVariants.map((variant) => variant.id).includes(variant.id)) {
            this.allowedVariants = this.allowedVariants.filter((m) => m.id !== variant.id);
        } else {
            this.allowedVariants.push(variant);
        }
        this.subSettings!.allowedVariants = this.allowedVariants.map((variant) => variant.id);
    }

    setVariant(variant: IrisVariant | undefined) {
        this.subSettings!.selectedVariant = variant?.id;
    }

    onEnabledChange() {
        this.subSettings!.enabled = this.enabled;
    }

    onEnable() {
        this.enabled = true;
        this.onEnabledChange();
    }

    onDisable() {
        this.enabled = false;
        this.onEnabledChange();
    }

    onInheritAllowedVariantsChange() {
        if (this.inheritAllowedVariants) {
            this.subSettings!.allowedVariants = undefined;
            this.allowedVariants = this.getAllowedVariants();
        } else {
            this.subSettings!.allowedVariants = this.allowedVariants.map((variant) => variant.id);
        }
    }

    onCategorySelectionChange(category: string) {
        if (!this.subSettings) {
            return;
        }
        if (!this.subSettings.enabledForCategories) {
            this.subSettings.enabledForCategories = [];
        }
        if (this.subSettings.enabledForCategories?.includes(category)) {
            this.subSettings.enabledForCategories = this.subSettings.enabledForCategories!.filter((c) => c !== category);
        } else {
            this.subSettings.enabledForCategories = [...(this.subSettings.enabledForCategories ?? []), category];
        }
    }

    onEventToggleChange(event: IrisEventType) {
        if (!this.subSettings) {
            return;
        }
        if (!this.subSettings.disabledProactiveEvents) {
            this.subSettings.disabledProactiveEvents = [];
        }
        if (this.subSettings.disabledProactiveEvents?.includes(event)) {
            this.subSettings.disabledProactiveEvents = this.subSettings.disabledProactiveEvents!.filter((c) => c !== event);
        } else {
            this.subSettings.disabledProactiveEvents = [...(this.subSettings.disabledProactiveEvents ?? []), event] as IrisEventType[];
        }
    }

    get inheritDisabled() {
        if (this.parentSubSettings) {
            return !this.parentSubSettings.enabled;
        }
        return false;
    }

    get isSettingsSwitchDisabled() {
        return this.inheritDisabled || (!this.isAdmin && this.settingsType !== this.EXERCISE);
    }

    /**
     * Updates the event disabled status map based on the parent settings
     * @private
     */
    private updateEventDisabledStatus(): void {
        this.exerciseChatEvents.forEach((event) => {
            const isDisabled =
                !this.subSettings?.enabled ||
                (this.parentSubSettings &&
                    !this.subSettings?.disabledProactiveEvents?.includes(event) &&
                    (this.parentSubSettings.disabledProactiveEvents?.includes(event) || !this.parentSubSettings.enabled));
            this.eventInParentDisabledStatusMap.set(event, isDisabled);
        });
    }
}
