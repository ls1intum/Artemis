import { Component, Signal, TemplateRef, computed, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { UserSettingsTitleBarService } from 'app/core/user/settings/shared/user-settings-title-bar.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-user-settings-title-bar',
    templateUrl: './user-settings-title-bar.component.html',
    imports: [NgTemplateOutlet, TranslateDirective],
})
export class UserSettingsTitleBarComponent {
    private userSettingsTitleBarService = inject(UserSettingsTitleBarService);

    readonly customTitleTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.userSettingsTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.userSettingsTitleBarService.actionsTemplate());
}
