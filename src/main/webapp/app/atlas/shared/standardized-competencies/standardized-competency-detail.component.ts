import { Component, input, output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
    styleUrls: ['./standardized-competency-detail.component.scss'],
    imports: [MarkdownDirective, TranslateDirective],
})
export class StandardizedCompetencyDetailComponent {
    // values for the knowledge area select
    competency = input.required<StandardizedCompetencyDTO>();
    knowledgeAreaTitle = input('');
    sourceString = input('');

    onClose = output<void>();

    // other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    close() {
        this.onClose.emit();
    }
}
