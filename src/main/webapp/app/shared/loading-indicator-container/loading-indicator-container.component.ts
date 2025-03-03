import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-loading-indicator-container',
    templateUrl: './loading-indicator-container.component.html',
    styleUrls: ['./loading-indicator-container.component.scss'],
    imports: [TranslateDirective],
})
export class LoadingIndicatorContainerComponent {
    isLoading = input<boolean>(false);
}
