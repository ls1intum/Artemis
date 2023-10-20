import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisModel } from 'app/entities/iris/settings/iris-model';
import { AccountService } from 'app/core/auth/account.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faTrash } from '@fortawesome/free-solid-svg-icons';

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
    modelOptional = false;

    isAdmin: boolean;

    allowedIrisModelsInherited: boolean;

    allowedIrisModels: IrisModel[];

    enabled: boolean;

    // Button types
    WARNING = ButtonType.WARNING;
    // Icons
    faTrash = faTrash;

    constructor(accountService: AccountService) {
        this.isAdmin = accountService.isAdmin();
    }

    ngOnInit() {
        this.enabled = this.subSettings?.enabled ?? false;
        this.allowedIrisModels = this.getAvailableModels();
        this.allowedIrisModelsInherited = !this.subSettings?.allowedModels && this.parentSubSettings !== undefined;
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

    getSelectedModelName(): string {
        return this.allIrisModels.find((model) => model.id === this.subSettings?.preferredModel)?.name ?? this.subSettings?.preferredModel ?? 'Inherit';
    }

    onAllowedIrisModelsSelectionChange() {
        this.allowedIrisModelsInherited = false;
        this.subSettings!.allowedModels = this.allowedIrisModels.map((model) => model.id);
    }

    setModel(model: IrisModel | undefined) {
        this.subSettings!.preferredModel = model?.id;
    }

    inheritAllowedModels() {
        this.allowedIrisModelsInherited = true;
        this.subSettings!.allowedModels = undefined;
    }

    onEnabledChange() {
        this.subSettings!.enabled = this.enabled;
    }
}
