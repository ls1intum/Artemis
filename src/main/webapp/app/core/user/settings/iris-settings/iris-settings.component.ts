import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-iris-settings',
    imports: [TranslateDirective],
    templateUrl: './iris-settings.component.html',
    styleUrl: './iris-settings.component.scss',
})
export class IrisSettingsComponent {}
