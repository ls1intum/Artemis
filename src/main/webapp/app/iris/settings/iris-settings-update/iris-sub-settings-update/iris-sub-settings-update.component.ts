import { Component, Input } from '@angular/core';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';

@Component({
    selector: 'jhi-iris-sub-settings-update',
    templateUrl: './iris-sub-settings-update.component.html',
})
export class IrisSubSettingsUpdateComponent {
    @Input()
    subSettings: IrisSubSettings;

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
}
