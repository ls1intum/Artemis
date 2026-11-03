import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-user-settings-title-bar',
    templateUrl: './user-settings-title-bar.component.html',
    styleUrl: './user-settings-title-bar.component.scss',
    imports: [TranslateDirective],
})
export class UserSettingsTitleBarComponent {
    readonly pageTitle = input.required<string>();
}
