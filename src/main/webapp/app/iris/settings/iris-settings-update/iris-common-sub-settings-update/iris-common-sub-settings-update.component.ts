import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { IrisSubSettings, IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisVariant } from 'app/entities/iris/settings/iris-variant';
import { AccountService } from 'app/core/auth/account.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-iris-common-sub-settings-update',
    templateUrl: './iris-common-sub-settings-update.component.html',
})
export class IrisCommonSubSettingsUpdateComponent implements OnInit, OnChanges {
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

    availableVariants: IrisVariant[] = [];

    allowedVariants: IrisVariant[] = [];

    enabled: boolean;

    categories: string[] = [];

    // Settings types
    EXERCISE = IrisSettingsType.EXERCISE;
    COURSE = IrisSettingsType.COURSE;
    TEXT_EXERCISE_CHAT = IrisSubSettingsType.TEXT_EXERCISE_CHAT;
    CHAT = IrisSubSettingsType.CHAT;
    // Button types
    WARNING = ButtonType.WARNING;
    // Icons
    faTrash = faTrash;

    protected readonly IrisSubSettings = IrisSubSettings;
    protected readonly IrisSubSettingsType = IrisSubSettingsType;

    constructor(
        accountService: AccountService,
        private irisSettingsService: IrisSettingsService,
        private courseManagementService: CourseManagementService,
        private exerciseService: ExerciseService,
        private alertService: AlertService,
    ) {
        this.isAdmin = accountService.isAdmin();
    }

    ngOnInit() {
        this.enabled = this.subSettings?.enabled ?? false;
        this.loadCategories();
        this.loadVariants();
        this.inheritAllowedVariants = !!(!this.subSettings?.allowedVariants && this.parentSubSettings);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.availableVariants) {
            this.allowedVariants = this.getAllowedVariants();
        }
        if (changes.subSettings) {
            this.enabled = this.subSettings?.enabled ?? false;
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

    get inheritDisabled() {
        if (this.parentSubSettings) {
            return !this.parentSubSettings.enabled;
        }
        return false;
    }

    get isSettingsSwitchDisabled() {
        return this.inheritDisabled || (!this.isAdmin && this.settingsType !== this.EXERCISE);
    }
}
