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
    protected readonly faCheck = faCheck;

    suggestions = input<string[] | undefined>([]);
    inconsistencies = input<string[] | undefined>([]);
    improvement = input<string | undefined>('');
    closeConsistencyWidget = output<void>();
    formattedConsistency = signal<{ inconsistentFaq: string; suggestion: string }[]>([]);

    constructor() {
        effect(() => {
            this.formattedConsistency.set(this.getInconsistencies());
        });
    }

    dismissConsistencyCheck(): void {
        this.closeConsistencyWidget.emit();
    }

    private getInconsistencies(): { inconsistentFaq: string; suggestion: string }[] {
        const inconsistencies = this.inconsistencies() ?? [];
        const suggestions = this.suggestions() ?? [];

        return inconsistencies.map((inconsistency, index) => ({
            inconsistentFaq: inconsistency,
            suggestion: suggestions[index] ?? '',
        }));
    }
}
