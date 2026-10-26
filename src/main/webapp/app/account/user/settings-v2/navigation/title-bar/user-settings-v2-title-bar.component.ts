import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-user-settings-v2-title-bar',
    templateUrl: './user-settings-v2-title-bar.component.html',
    styleUrl: './user-settings-v2-title-bar.component.scss',
    imports: [TranslateDirective],
})
export class UserSettingsV2TitleBarComponent {
    readonly pageTitle = input.required<string>();
}
