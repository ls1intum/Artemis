import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-llm-selection-info',
    templateUrl: './llm-selection-info.component.html',
    styleUrls: ['./llm-selection-info.component.scss'],
    imports: [TranslateDirective],
})
export class LlmSelectionInfoComponent {}
