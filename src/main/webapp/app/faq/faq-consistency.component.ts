import { Component, effect, input, output, signal } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'faq-consistency-check',
    templateUrl: './faq-consistency.component.html',
    imports: [TranslateDirective, FontAwesomeModule],
    standalone: true,
})
export class FaqConsistencyComponent {
    suggestions = input<string[]>([]);
    inconsistencies = input<string[]>([]);
    improvement = input<string>('');
    close = output<void>();

    formattedConsistency = signal<{ inconsistentFaq: string; suggestion: string }[]>([]);

    protected readonly faCheck = faCheck;

    constructor() {
        effect(() => {
            this.formattedConsistency.set(
                this.inconsistencies().map((inconsistency, index) => {
                    const suggestion = this.suggestions()[index];
                    return {
                        inconsistentFaq: inconsistency,
                        suggestion: suggestion,
                    };
                }),
            );
        });
    }

    dismissConsistencyCheck(): void {
        this.close.emit();
    }
}
