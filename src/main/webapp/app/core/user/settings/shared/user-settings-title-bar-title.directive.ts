import { Directive, OnDestroy, TemplateRef, inject } from '@angular/core';
import { UserSettingsTitleBarService } from 'app/core/user/settings/shared/user-settings-title-bar.service';

@Directive({
    selector: '[userSettingsTitleBarTitle]',
})
export class UserSettingsTitleBarTitleDirective implements OnDestroy {
    private templateRef: TemplateRef<any> = inject(TemplateRef);
    private userSettingsTitleBarService: UserSettingsTitleBarService = inject(UserSettingsTitleBarService);

    constructor() {
        this.userSettingsTitleBarService.setTitleTemplate(this.templateRef);
    }

    ngOnDestroy() {
        const isCurrentDirectiveInstance = this.userSettingsTitleBarService.titleTemplate() === this.templateRef;
        if (isCurrentDirectiveInstance) {
            this.userSettingsTitleBarService.setTitleTemplate(undefined);
        }
    }
}
