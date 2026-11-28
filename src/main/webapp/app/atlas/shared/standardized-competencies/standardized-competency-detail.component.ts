import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
    imports: [MarkdownDirective, TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective, FaIconComponent],
})
export class StandardizedCompetencyDetailComponent {
    protected readonly faTimes = faTimes;

    // values for the knowledge area select
    competency = input.required<StandardizedCompetencyDTO>();
    knowledgeAreaTitle = input('');
    sourceString = input('');

    onClose = output<void>();

    close() {
        this.onClose.emit();
    }
}
