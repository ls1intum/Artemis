import { Injectable, Signal, TemplateRef, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CourseTitleBarService {
    private _titleTemplate = signal<TemplateRef<any> | undefined>(undefined);
    private _actionsTemplate = signal<TemplateRef<any> | undefined>(undefined);

    readonly titleTemplate: Signal<TemplateRef<any> | undefined> = this._titleTemplate;
    readonly actionsTemplate: Signal<TemplateRef<any> | undefined> = this._actionsTemplate;

    setTitleTemplate(template: TemplateRef<any> | undefined) {
        this._titleTemplate.set(template);
    }
    setActionsTemplate(template: TemplateRef<any> | undefined) {
        this._actionsTemplate.set(template);
    }
}
