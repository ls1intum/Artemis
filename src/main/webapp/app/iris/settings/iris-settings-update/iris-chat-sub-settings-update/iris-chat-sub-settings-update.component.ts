import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { IrisTemplate } from 'app/entities/iris/settings/iris-template';
import { IrisChatSubSettings, IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';

@Component({
    selector: 'jhi-iris-chat-sub-settings-update',
    templateUrl: './iris-chat-sub-settings-update.component.html',
})
export class IrisChatSubSettingsUpdateComponent implements OnInit, OnChanges {
    @Input()
    subSettings?: IrisChatSubSettings;

    @Input()
    parentSubSettings?: IrisChatSubSettings;

    @Input()
    rateLimitSettable = false;

    @Output()
    onChanges = new EventEmitter<IrisSubSettings>();

    previousTemplate?: IrisTemplate;

    isAdmin: boolean;

    templateContent: string;

    ngOnInit(): void {
        this.templateContent = this.subSettings?.template?.content ?? this.parentSubSettings?.template?.content ?? '';
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.subSettings || changes.parentSubSettings) {
            this.templateContent = this.subSettings?.template?.content ?? this.parentSubSettings?.template?.content ?? '';
        }
    }

    onInheritTemplateChanged() {
        if (this.subSettings?.template) {
            this.previousTemplate = this.subSettings?.template;
            this.subSettings.template = undefined;
            this.templateContent = this.parentSubSettings?.template?.content ?? '';
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = '';
            this.subSettings!.template = this.previousTemplate ?? irisTemplate;
        }
    }

    onTemplateChanged() {
        if (this.subSettings?.template) {
            this.subSettings.template.content = this.templateContent;
        } else {
            const irisTemplate = new IrisTemplate();
            irisTemplate.content = this.templateContent;
            this.subSettings!.template = irisTemplate;
        }
    }
}
