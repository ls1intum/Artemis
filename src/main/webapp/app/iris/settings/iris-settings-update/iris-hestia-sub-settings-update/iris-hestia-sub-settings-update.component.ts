import { Component, Input, OnInit } from '@angular/core';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisHestiaSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';

@Component({
    selector: 'jhi-iris-hestia-sub-settings-update',
    templateUrl: './iris-hestia-sub-settings-update.component.html',
})
export class IrisHestiaSubSettingsUpdateComponent implements OnInit {
    @Input()
    subSettings: IrisHestiaSubSettings;

    @Input()
    parentSubSettings?: IrisHestiaSubSettings;

    @Input()
    templateOptional = false;

    previousTemplate?: IrisTemplate;

    isAdmin: boolean;

    templateContent: string;

    ngOnInit(): void {
        this.templateContent = this.subSettings.template?.content ?? this.parentSubSettings?.template?.content ?? '';
    }

    onInheritTemplateChanged() {
        if (this.subSettings.template) {
            this.previousTemplate = this.subSettings.template;
            this.subSettings.template = undefined;
            this.templateContent = this.parentSubSettings?.template?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings.template = this.previousTemplate ?? irisTemplate;
        }
    }

    onTemplateChanged() {
        if (this.subSettings.template) {
            this.subSettings.template.content = this.templateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.templateContent;
            this.subSettings.template = irisTemplate;
        }
    }
}
