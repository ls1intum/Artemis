import { Component, input, output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ButtonModule } from 'primeng/button';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
    styleUrls: ['./standardized-competency-detail.component.scss'],
    imports: [HtmlForMarkdownPipe, TranslateDirective, ButtonModule],
})
export class StandardizedCompetencyDetailComponent {
    // values for the knowledge area select
    competency = input.required<StandardizedCompetencyDTO>();
    knowledgeAreaTitle = input('');
    sourceString = input('');

    onClose = output<void>();

    close() {
        this.onClose.emit();
    }
}
