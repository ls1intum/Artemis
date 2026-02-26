import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';

/**
 * Displays the details of a single Memiris memory, including learnings and connections.
 * The component is presentational and expects a fully resolved details object.
 */
@Component({
    selector: 'jhi-memiris-memory-details',
    standalone: true,
    imports: [CommonModule, TranslateDirective],
    templateUrl: './memiris-memory-details.component.html',
})
export class MemirisMemoryDetailsComponent {
    /**
     * The detailed memory object to render. When undefined, a fallback message is shown.
     */
    details = input<MemirisMemoryWithRelationsDTO | undefined>();
}
