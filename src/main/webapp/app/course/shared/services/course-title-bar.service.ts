import { Injectable, Signal, TemplateRef, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CourseTitleBarService {
    private currentTitleTemplate = signal<TemplateRef<unknown> | undefined>(undefined);
    private currentActionsTemplate = signal<TemplateRef<unknown> | undefined>(undefined);
    private currentToolbarTemplate = signal<TemplateRef<unknown> | undefined>(undefined);

    readonly titleTemplate: Signal<TemplateRef<unknown> | undefined> = this.currentTitleTemplate;
    readonly actionsTemplate: Signal<TemplateRef<unknown> | undefined> = this.currentActionsTemplate;
    readonly toolbarTemplate: Signal<TemplateRef<unknown> | undefined> = this.currentToolbarTemplate;

    setTitleTemplate(template: TemplateRef<unknown> | undefined) {
        this.currentTitleTemplate.set(template);
    }
    setActionsTemplate(template: TemplateRef<unknown> | undefined) {
        this.currentActionsTemplate.set(template);
    }
    setToolbarTemplate(template: TemplateRef<unknown> | undefined) {
        this.currentToolbarTemplate.set(template);
    }
}
