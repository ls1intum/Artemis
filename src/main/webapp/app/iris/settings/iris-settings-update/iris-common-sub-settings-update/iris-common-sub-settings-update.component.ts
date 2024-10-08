import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { IrisSubSettings, IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisModel } from 'app/entities/iris/settings/iris-model';
import { AccountService } from 'app/core/auth/account.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';

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
    allIrisModels: IrisModel[];

    @Input()
    settingsType: IrisSettingsType;

    @Output()
    onChanges = new EventEmitter<IrisSubSettings>();

    isAdmin: boolean;

    inheritAllowedModels: boolean;

    allowedIrisModels: IrisModel[];

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
        this.allowedIrisModels = this.getAvailableModels();
        this.inheritAllowedModels = !!(!this.subSettings?.allowedModels && this.parentSubSettings);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.allIrisModels) {
            this.allowedIrisModels = this.getAvailableModels();
        }
        if (changes.subSettings) {
            this.enabled = this.subSettings?.enabled ?? false;
        }
    }

    getAvailableModels(): IrisModel[] {
        return this.allIrisModels.filter((model) => (this.subSettings?.allowedModels ?? this.parentSubSettings?.allowedModels ?? []).includes(model.id));
    }

    getPreferredModelName(): string | undefined {
        return this.allIrisModels.find((model) => model.id === this.subSettings?.preferredModel)?.name ?? this.subSettings?.preferredModel;
    }

    getPreferredModelNameParent(): string | undefined {
        return this.allIrisModels.find((model) => model.id === this.parentSubSettings?.preferredModel)?.name ?? this.parentSubSettings?.preferredModel;
    }

    onAllowedIrisModelsSelectionChange(model: IrisModel) {
        this.inheritAllowedModels = false;
        if (this.allowedIrisModels.includes(model)) {
            this.allowedIrisModels = this.allowedIrisModels.filter((m) => m !== model);
        } else {
            this.allowedIrisModels.push(model);
        }
        this.subSettings!.allowedModels = this.allowedIrisModels.map((model) => model.id);
    }

    setModel(model: IrisModel | undefined) {
        this.subSettings!.preferredModel = model?.id;
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

    onInheritAllowedModelsChange() {
        if (this.inheritAllowedModels) {
            this.subSettings!.allowedModels = undefined;
            this.allowedIrisModels = this.getAvailableModels();
        } else {
            this.subSettings!.allowedModels = this.allowedIrisModels.map((model) => model.id);
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
