import { Component, EventEmitter, Input, Output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
    styleUrls: ['./standardized-competency-detail.component.scss'],
    imports: [ArtemisSharedCommonModule, ArtemisMarkdownModule],
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
