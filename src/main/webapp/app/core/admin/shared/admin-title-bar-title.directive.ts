import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { AdminTitleBarService } from 'app/core/admin/shared/admin-title-bar.service';

@Directive({
    selector: '[adminTitleBarTitle]',
})
export class AdminTitleBarTitleDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private adminTitleBarService: AdminTitleBarService = inject(AdminTitleBarService);

    constructor() {
        this.adminTitleBarService.setTitleTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isCurrentDirectiveInstance = this.adminTitleBarService.titleTemplate() === this.templateRef;
        if (isCurrentDirectiveInstance) {
            this.adminTitleBarService.setTitleTemplate(undefined);
        }
    }
}
