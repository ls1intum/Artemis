import { Component, effect, input, output, signal } from '@angular/core';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'faq-consistency-check',
    templateUrl: './faq-consistency.component.html',
    imports: [TranslateDirective, TumAetUiMessageComponent],
})
export class FaqConsistencyComponent {
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
