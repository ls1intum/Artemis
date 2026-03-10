import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    DestroyRef,
    ElementRef,
    Injector,
    OnInit,
    afterNextRender,
    computed,
    inject,
    input,
    model,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPaperPlane, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ButtonDirective } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { PROMPT_LENGTH_WARNING_THRESHOLD } from 'app/programming/manage/shared/problem-statement.utils';

/**
 * Floating refinement button component that appears near text selection.
 * Expands to show an input field for entering refinement instructions.
 */
@Component({
    selector: 'jhi-inline-refinement-button',
    standalone: true,
    imports: [FormsModule, FaIconComponent, ArtemisTranslatePipe, ButtonDirective],
    templateUrl: './inline-refinement-button.component.html',
    styleUrls: ['./inline-refinement-button.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InlineRefinementButtonComponent implements OnInit {
    private readonly translateService = inject(TranslateService);
    private readonly cdr = inject(ChangeDetectorRef);
    private readonly destroyRef = inject(DestroyRef);
    private readonly injector = inject(Injector);

    // Input: Position where the button should appear
    top = input.required<number>();
    left = input.required<number>();

    // Input: The selected text to refine
    selectedText = input.required<string>();

    // Input: Selection position info for character-level targeting
    startLine = input.required<number>();
    endLine = input.required<number>();
    startColumn = input.required<number>();
    endColumn = input.required<number>();

    // Output: Emits when user submits refinement instruction (includes position info)
    refine = output<{
        instruction: string;
        startLine: number;
        endLine: number;
        startColumn: number;
        endColumn: number;
    }>();

    // Output: Emits when user closes the button/input
    closeRefinement = output<void>();

    // State
    isExpanded = signal(false);
    instruction = model('');
    // Input: Whether submission is in progress
    isLoading = input<boolean>(false);

    // Icons
    readonly facArtemisIntelligence = facArtemisIntelligence;
    readonly faPaperPlane = faPaperPlane;
    readonly faTimes = faTimes;

    /** Maximum instruction length for inline refinement. Must match HyperionUtils.MAX_INSTRUCTION_LENGTH. */
    readonly MAX_INSTRUCTION_LENGTH = 500;

    /** Whether the instruction is near the character limit. */
    readonly isNearLimit = computed(() => this.instruction().length >= this.MAX_INSTRUCTION_LENGTH * PROMPT_LENGTH_WARNING_THRESHOLD);

    // Reference to input element for focus
    inputElement = viewChild<ElementRef<HTMLInputElement>>('instructionInput');

    ngOnInit(): void {
        // Subscribe to language changes to trigger re-render for translations
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.cdr.markForCheck();
        });
    }

    /**
     * Expands the button to show the input field.
     */
    expand(): void {
        this.isExpanded.set(true);
        // Focus the input after the DOM updates following expansion
        afterNextRender(
            () => {
                this.inputElement()?.nativeElement.focus();
            },
            { injector: this.injector },
        );
    }

    /**
     * Submits the refinement instruction.
     */
    submit(): void {
        const text = this.instruction().trim();
        if (!text || this.isLoading()) return;

        this.refine.emit({
            instruction: text,
            startLine: this.startLine(),
            endLine: this.endLine(),
            startColumn: this.startColumn(),
            endColumn: this.endColumn(),
        });
    }

    /**
     * Handles Enter key to submit.
     */
    onKeydown(event: KeyboardEvent): void {
        event.stopPropagation();
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
