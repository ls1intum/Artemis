import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { AdminTitleBarService } from 'app/core/admin/shared/admin-title-bar.service';

@Directive({
    selector: '[adminTitleBarActions]',
})
export class AdminTitleBarActionsDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private adminTitleBarService: AdminTitleBarService = inject(AdminTitleBarService);

    constructor() {
        this.adminTitleBarService.setActionsTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isTemplateOfCurrentDirectiveInstance = this.adminTitleBarService.actionsTemplate() === this.templateRef;
        if (isTemplateOfCurrentDirectiveInstance) {
            this.adminTitleBarService.setActionsTemplate(undefined);
        }
    }
}
