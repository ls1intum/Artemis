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
        const ids = this.faqIds() ?? [];
        return ids.map((id) => this.buildLinkToFaq(id));
    }

    private buildLinkToFaq(faqId: number): string {
        const course = this.courseId();
        return `/courses/${course}/faq?faqId=${faqId}`;
    }

    private buildInconsistencyTextWithLinks(): string[] {
        const inconsistencies = this.inconsistencies() ?? [];
        const faqIds = this.faqIds() ?? [];
        const links = this.linksToFaqs ?? [];

        if (inconsistencies.length !== faqIds.length) {
            throw new Error('Inconsistencies and FAQ IDs arrays must have the same length');
        }

        return inconsistencies.map((inconsistency, index) => this.mergeInconsistencyWithLink(inconsistency, faqIds[index], links[index]));
    }

    private mergeInconsistencyWithLink(inconsistency: string, faqId?: number, link?: string): string {
        if (faqId !== undefined && link) {
            return `${inconsistency} <a href="${link}" target="_blank" rel="noopener noreferrer">#${faqId}</a>`;
        }
        return inconsistency;
    }

    dismissConsistencyCheck(): void {
        this.closeConsistencyWidget.emit();
    }
}
