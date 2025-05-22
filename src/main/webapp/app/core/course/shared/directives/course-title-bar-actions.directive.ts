import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

@Directive({
    selector: '[titleBarActions]',
})
export class CourseTitleBarActionsDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private courseTitleBarService: CourseTitleBarService = inject(CourseTitleBarService);

    constructor() {
        this.courseTitleBarService.setActionsTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isTemplateOfCurrentDirectiveInstance = this.courseTitleBarService.actionsTemplate() === this.templateRef;
        if (isTemplateOfCurrentDirectiveInstance) {
            this.courseTitleBarService.setActionsTemplate(undefined);
        }
    }
}
