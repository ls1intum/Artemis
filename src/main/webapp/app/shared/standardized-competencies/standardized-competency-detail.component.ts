import { Component, EventEmitter, Input, Output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
    styleUrls: ['./standardized-competency-detail.component.scss'],
    imports: [HtmlForMarkdownPipe, TranslateDirective],
})
export class StandardizedCompetencyDetailComponent {
    // values for the knowledge area select
    @Input({ required: true }) competency: StandardizedCompetencyDTO;
    @Input() knowledgeAreaTitle = '';
    @Input() sourceString = '';

    @Output() onClose = new EventEmitter<void>();

    // other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    close() {
        this.onClose.emit();
    }
}
