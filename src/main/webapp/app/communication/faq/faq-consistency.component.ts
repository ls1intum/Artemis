import { Component, effect, input, output } from '@angular/core';
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

    inconsistencies = input<string[] | undefined>([]);
    improvement = input<string | undefined>('');
    faqIds = input<number[] | undefined>([]);
    courseId = input<number>(0);
    closeConsistencyWidget = output<void>();
    linksToFaqs: string[] = [];
    fullInconsistencyText: string[] = [];

    constructor() {
        effect(() => {
            this.linksToFaqs = this.buildLinksToFaqs();
            this.fullInconsistencyText = this.buildInconsistencyTextWithLinks();
        });
    }

    private buildLinksToFaqs(): string[] {
        const ids = this.faqIds();
        const course = this.courseId();

        if (!ids || ids.length === 0 || !course) {
            return [];
        }

        return ids.map((id) => `/courses/${course}/faq?faqId=${id}`);
    }

    buildInconsistencyTextWithLinks(): string[] {
        const inconsistencies = this.inconsistencies() ?? [];
        const faqIds = this.faqIds() ?? [];
        const links = this.linksToFaqs ?? [];

        const merged: string[] = [];

        for (let i = 0; i < inconsistencies.length; i++) {
            const inconsistency = inconsistencies[i];
            const id = faqIds[i];
            const link = links[i];

            if (id !== undefined && link) {
                merged.push(`${inconsistency} <a href="${link}" target="_blank" rel="noopener noreferrer">#${id}</a>`);
            } else {
                merged.push(inconsistency);
            }
        }

        return merged;
    }

    dismissConsistencyCheck(): void {
        this.closeConsistencyWidget.emit();
    }
}
