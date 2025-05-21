import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

@Directive({
    selector: '[titleBarTitle]',
})
export class CourseTitleBarTitleDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private courseTitleBarService: CourseTitleBarService = inject(CourseTitleBarService);

    constructor() {
        this.courseTitleBarService.setTitleTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isCurrentDirectiveInstance = this.courseTitleBarService.titleTemplate() === this.templateRef;
        if (isCurrentDirectiveInstance) {
            this.courseTitleBarService.setTitleTemplate(undefined);
        }
    }
}
