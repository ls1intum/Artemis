import { ChangeDetectionStrategy, Component, Signal, TemplateRef, computed, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { AdminTitleBarService } from 'app/admin/shared/admin-title-bar.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-admin-title-bar',
    // The global rules of layout.css pin the controls that pages project into this bar to the height of the bar.
    host: { class: 'title-bar-controls' },
    templateUrl: './admin-title-bar.component.html',
    styleUrl: './admin-title-bar.component.css',
    imports: [NgTemplateOutlet, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminTitleBarComponent {
    private adminTitleBarService = inject(AdminTitleBarService);

    readonly customTitleTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.adminTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.adminTitleBarService.actionsTemplate());
}
