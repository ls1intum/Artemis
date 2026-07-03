import { Injectable, Signal, TemplateRef, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CourseTitleBarService {
    private currentTitleTemplate = signal<TemplateRef<unknown> | undefined>(undefined);
    private currentActionsTemplate = signal<TemplateRef<unknown> | undefined>(undefined);

    readonly titleTemplate: Signal<TemplateRef<unknown> | undefined> = this.currentTitleTemplate;
    readonly actionsTemplate: Signal<TemplateRef<unknown> | undefined> = this.currentActionsTemplate;

    setTitleTemplate(template: TemplateRef<unknown> | undefined) {
        this.currentTitleTemplate.set(template);
    }
    setActionsTemplate(template: TemplateRef<unknown> | undefined) {
        this.currentActionsTemplate.set(template);
    }
}
