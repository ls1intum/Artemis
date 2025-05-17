import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

@Directive({
    selector: '[titleBarActions]',
    standalone: true,
})
export class CourseTitleBarActionsDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private courseTitleBarService: CourseTitleBarService = inject(CourseTitleBarService);

    constructor() {
        this.courseTitleBarService.setActionsTpl(this.templateRef);
    }

    ngOnDestroy() {
        // Only clear the template if it's the one currently set by this directive instance
        if (this.courseTitleBarService.actionsTpl() === this.templateRef) {
            this.courseTitleBarService.setActionsTpl(undefined);
        }
    }
}
