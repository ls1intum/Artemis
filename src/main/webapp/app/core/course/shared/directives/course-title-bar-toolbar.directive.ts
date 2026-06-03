import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

@Directive({
    selector: '[titleBarToolbar]',
})
export class CourseTitleBarToolbarDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private courseTitleBarService: CourseTitleBarService = inject(CourseTitleBarService);

    constructor() {
        this.courseTitleBarService.setToolbarTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isTemplateOfCurrentDirectiveInstance = this.courseTitleBarService.toolbarTemplate() === this.templateRef;
        if (isTemplateOfCurrentDirectiveInstance) {
            this.courseTitleBarService.setToolbarTemplate(undefined);
        }
    }
}
