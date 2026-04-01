import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Wrapper component for embedded PDF viewer mode.
 * Provides the border and styling for inline PDF viewing.
 */
@Component({
    selector: 'jhi-pdf-embedded-wrapper',
    standalone: true,
    imports: [],
    templateUrl: './pdf-embedded-wrapper.component.html',
    styleUrls: ['./pdf-embedded-wrapper.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfEmbeddedWrapperComponent {}
