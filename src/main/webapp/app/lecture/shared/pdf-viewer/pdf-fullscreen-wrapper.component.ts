import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Wrapper component for fullscreen PDF viewer mode.
 * Handles the overlay, close button, loading spinner, and focus management.
 */
@Component({
    selector: 'jhi-pdf-fullscreen-wrapper',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './pdf-fullscreen-wrapper.component.html',
    styleUrls: ['./pdf-fullscreen-wrapper.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfFullscreenWrapperComponent {
    isLoading = input.required<boolean>();
    closeFullscreen = output<void>();

    readonly fullscreenWindow = viewChild<ElementRef<HTMLDivElement>>('fullscreenWindow');

    protected readonly faXmark = faXmark;

    constructor() {
        // Auto-focus fullscreen window when rendered (for ESC key to work)
        effect(() => {
            const windowElement = this.fullscreenWindow()?.nativeElement;
            if (windowElement) {
                // Use setTimeout to ensure the element is fully rendered
                setTimeout(() => windowElement.focus(), 0);
            }
        });
    }

    onOverlayClick(): void {
        this.closeFullscreen.emit();
    }

    onCloseClick(): void {
        this.closeFullscreen.emit();
    }

    onEscapeKey(): void {
        this.closeFullscreen.emit();
    }
}
