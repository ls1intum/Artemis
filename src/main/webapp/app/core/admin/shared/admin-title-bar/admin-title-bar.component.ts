import { Component, Signal, TemplateRef, computed, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { AdminTitleBarService } from 'app/core/admin/shared/admin-title-bar.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-admin-title-bar',
    templateUrl: './admin-title-bar.component.html',
    imports: [NgTemplateOutlet, TranslateDirective],
})
export class AdminTitleBarComponent {
    private adminTitleBarService = inject(AdminTitleBarService);

    readonly customTitleTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.adminTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.adminTitleBarService.actionsTemplate());
}
