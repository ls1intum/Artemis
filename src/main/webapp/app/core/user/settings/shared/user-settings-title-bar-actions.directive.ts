import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { UserSettingsTitleBarService } from 'app/core/user/settings/shared/user-settings-title-bar.service';

@Directive({
    selector: '[userSettingsTitleBarActions]',
})
export class UserSettingsTitleBarActionsDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private userSettingsTitleBarService: UserSettingsTitleBarService = inject(UserSettingsTitleBarService);

    constructor() {
        this.userSettingsTitleBarService.setActionsTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isTemplateOfCurrentDirectiveInstance = this.userSettingsTitleBarService.actionsTemplate() === this.templateRef;
        if (isTemplateOfCurrentDirectiveInstance) {
            this.userSettingsTitleBarService.setActionsTemplate(undefined);
        }
    }
}
