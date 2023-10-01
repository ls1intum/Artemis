import { Component, Input } from '@angular/core';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisModel } from 'app/entities/iris/settings/iris-model';

@Component({
    selector: 'jhi-iris-sub-settings-update',
    templateUrl: './iris-sub-settings-update.component.html',
})
export class IrisSubSettingsUpdateComponent {
    @Input()
    subSettings: IrisSubSettings;

    @Input()
    models: IrisModel[];

    @Input()
    modelOptional = false;

    @Input()
    templateOptional = false;

    previousTemplate?: IrisTemplate;

    onInheritTemplateChanged() {
        if (this.subSettings.template) {
            this.previousTemplate = this.subSettings.template;
            this.subSettings.template = undefined;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings.template = this.previousTemplate ?? irisTemplate;
        }
    }

    getSelectedModelName(): string {
        return this.models.find((model) => model.id === this.subSettings.preferredModel)?.name ?? this.subSettings.preferredModel ?? 'None';
    }

    setModel(model: IrisModel | undefined) {
        this.subSettings.preferredModel = model?.id;
    }
}
