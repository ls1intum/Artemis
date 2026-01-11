import { ChangeDetectionStrategy, Component, ElementRef, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagic, faPaperPlane, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Floating refinement button component that appears near text selection.
 * Expands to show an input field for entering refinement instructions.
 */
@Component({
    selector: 'jhi-inline-refinement-button',
    standalone: true,
    imports: [FormsModule, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './inline-refinement-button.component.html',
    styleUrls: ['./inline-refinement-button.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InlineRefinementButtonComponent {
    // Input: Position where the button should appear
    top = input.required<number>();
    left = input.required<number>();

    // Input: The selected text to refine
    selectedText = input.required<string>();

    // Output: Emits when user submits refinement instruction
    refine = output<{ selectedText: string; instruction: string }>();

    // Output: Emits when user closes the button/input
    closeRefinement = output<void>();

    // State
    isExpanded = signal(false);
    instruction = '';
    isSubmitting = signal(false);

    // Icons
    readonly faMagic = faMagic;
    readonly faPaperPlane = faPaperPlane;
    readonly faTimes = faTimes;

    // Reference to input element for focus
    inputElement = viewChild<ElementRef<HTMLInputElement>>('instructionInput');

    /**
     * Expands the button to show the input field.
     */
    expand(): void {
        this.isExpanded.set(true);
        // Focus the input after expansion
        setTimeout(() => {
            this.inputElement()?.nativeElement.focus();
        }, 50);
    }

    /**
     * Submits the refinement instruction.
     */
    submit(): void {
        const text = this.instruction.trim();
        if (!text || this.isSubmitting()) return;

        this.isSubmitting.set(true);
        this.refine.emit({
            selectedText: this.selectedText(),
            instruction: text,
        });
    }

    /**
     * Handles Enter key to submit.
     */
    onKeydown(event: KeyboardEvent): void {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            this.submit();
        } else if (event.key === 'Escape') {
            this.closeRefinement.emit();
        }
    }

    /**
     * Closes the component.
     */
    handleClose(): void {
        this.closeRefinement.emit();
    }
}
