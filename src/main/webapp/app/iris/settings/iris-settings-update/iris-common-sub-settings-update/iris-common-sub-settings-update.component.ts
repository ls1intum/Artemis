import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { IrisSubSettings, IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisVariant } from 'app/entities/iris/settings/iris-variant';
import { AccountService } from 'app/core/auth/account.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';

@Component({
    selector: 'jhi-iris-common-sub-settings-update',
    templateUrl: './iris-common-sub-settings-update.component.html',
})
export class IrisCommonSubSettingsUpdateComponent implements OnInit, OnChanges {
    private irisSettingsService = inject(IrisSettingsService);

    @Input() subSettings?: IrisSubSettings;
    @Input() parentSubSettings?: IrisSubSettings;
    @Input() settingsType: IrisSettingsType;
    @Output() onChanges = new EventEmitter<IrisSubSettings>();

    isAdmin: boolean;
    inheritAllowedVariants: boolean;
    availableVariants: IrisVariant[] = [];
    allowedVariants: IrisVariant[] = [];
    enabled: boolean;

    // Settings types
    EXERCISE = IrisSettingsType.EXERCISE;
    COURSE = IrisSettingsType.COURSE;
    // Button types
    WARNING = ButtonType.WARNING;
    // Icons
    faTrash = faTrash;

    constructor() {
        const accountService = inject(AccountService);
        this.isAdmin = accountService.isAdmin();
    }

    ngOnInit() {
        this.enabled = this.subSettings?.enabled ?? false;
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

    get inheritDisabled() {
        if (this.parentSubSettings) {
            return !this.parentSubSettings.enabled;
        }
        return false;
    }
    get isSettingsSwitchDisabled() {
        return this.inheritDisabled || (!this.isAdmin && this.settingsType !== this.EXERCISE);
    }
    protected readonly IrisSubSettings = IrisSubSettings;
    protected readonly IrisSubSettingsType = IrisSubSettingsType;
}
