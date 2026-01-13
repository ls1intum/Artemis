import { Injectable, Signal, TemplateRef, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class UserSettingsTitleBarService {
    private currentTitleTemplate = signal<TemplateRef<any> | undefined>(undefined);
    private currentActionsTemplate = signal<TemplateRef<any> | undefined>(undefined);

    readonly titleTemplate: Signal<TemplateRef<any> | undefined> = this.currentTitleTemplate;
    readonly actionsTemplate: Signal<TemplateRef<any> | undefined> = this.currentActionsTemplate;

    setTitleTemplate(template: TemplateRef<any> | undefined) {
        this.currentTitleTemplate.set(template);
    }

    setActionsTemplate(template: TemplateRef<any> | undefined) {
        this.currentActionsTemplate.set(template);
    }
}
