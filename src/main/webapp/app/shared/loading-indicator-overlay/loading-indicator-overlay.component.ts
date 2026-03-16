import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-loading-indicator-overlay',
    imports: [TranslateDirective],
    templateUrl: './loading-indicator-overlay.component.html',
})
export class LoadingIndicatorOverlayComponent {}
