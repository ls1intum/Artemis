import { Component, effect, input, output, signal } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'faq-consistency-check',
    templateUrl: './faq-consistency-component.html',
    imports: [TranslateDirective, FontAwesomeModule],
    standalone: true,
})
export class FaqConsistencyComponent {
    protected readonly faCheck = faCheck;

    suggestions = input<string[]>([]);
    inconsistencies = input<string[]>([]);
    improvement = input<string>('');
    closeConsistencyWidget = output<void>();

    formattedConsistency = signal<{ inconsistentFaq: string; suggestion: string }[]>([]);

    constructor() {
        effect(() => {
            effect(() => {
                this.formattedConsistency.set(this.getInconsistencies());
            });
        });
    }

    dismissConsistencyCheck(): void {
        this.closeConsistencyWidget.emit();
    }

    private getInconsistencies(): { inconsistentFaq: string; suggestion: string }[] {
        return this.inconsistencies().map((inconsistency, index) => {
            const suggestion = this.suggestions()[index];
            return {
                inconsistentFaq: inconsistency,
                suggestion: suggestion,
            };
        });
    }
}
