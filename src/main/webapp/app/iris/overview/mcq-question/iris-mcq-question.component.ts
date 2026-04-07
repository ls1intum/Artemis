import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { McqData, McqQuestionData } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { IrisCitationTextComponent } from 'app/iris/overview/citation-text/iris-citation-text.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Interactive multiple-choice question component rendered inside Iris chat messages.
 * Displays a question with selectable options, handles submission, and shows feedback.
 */
@Component({
    selector: 'jhi-iris-mcq-question',
    standalone: true,
    imports: [TranslateDirective, IrisCitationTextComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './iris-mcq-question.component.html',
    styleUrl: './iris-mcq-question.component.scss',
})
export class IrisMcqQuestionComponent {
    /** The MCQ payload to render, containing the question, options, and explanation. */
    mcqData = input.required<McqData | McqQuestionData>();

    /** Citation metadata for rendering citation bubbles in the explanation. */
    citationInfo = input<IrisCitationMetaDTO[]>([]);

    // Optional inputs for pre-populated state from carousel parent
    initialSelectedIndex = input<number | undefined>(undefined);
    initialSubmitted = input<boolean>(false);

    // Output event for carousel parent
    answerChanged = output<{ selectedIndex: number | undefined; submitted: boolean }>();

    private readonly instanceId = window.crypto.randomUUID?.() ?? Math.random().toString(36).slice(2);
    /** Unique id for the question label element, used to link aria-labelledby across instances. */
    readonly questionLabelId = `mcq-question-label-${this.instanceId}`;

    selectedIndex = signal<number | undefined>(undefined);
    submitted = signal(false);

    constructor() {
        // Restore state from carousel parent inputs
        effect(() => {
            const idx = this.initialSelectedIndex();
            const sub = this.initialSubmitted();
            this.selectedIndex.set(idx);
            this.submitted.set(sub);
        });

        // Restore state from persisted response on standalone MCQ
        effect(() => {
            const data = this.mcqData();
            if ('response' in data && data.response) {
                this.selectedIndex.set(data.response.selectedIndex);
                this.submitted.set(data.response.submitted);
            }
        });
    }

    /**
     * Selects an option by index. No-op if the question has already been submitted.
     * @param index the index of the option to select
     */
    selectOption(index: number): void {
        if (this.submitted()) {
            return;
        }
        this.selectedIndex.set(index);
        this.answerChanged.emit({ selectedIndex: index, submitted: false });
    }

    /**
     * Submits the current selection and locks the question from further changes.
     */
    submit(): void {
        if (this.selectedIndex() === undefined || this.submitted()) {
            return;
        }
        this.submitted.set(true);
        this.answerChanged.emit({ selectedIndex: this.selectedIndex(), submitted: true });
    }

    /**
     * Converts a zero-based index to a letter label (0 -> 'A', 1 -> 'B', etc.).
     * @param index the option index
     * @returns the corresponding letter label
     */
    optionLabel(index: number): string {
        return String.fromCharCode(65 + index);
    }

    /**
     * Strips leading letter/number prefixes like "A.", "A)", "A:", "1.", "1)" from option text.
     * Only strips when followed by an explicit delimiter (. ) :), not a plain space,
     * to avoid removing meaningful text that happens to start with a letter.
     * @param text the raw option text
     * @returns the cleaned option text
     */
    cleanOptionText(text: string): string {
        return text.replace(/^[A-Da-d1-4][.):]\s*/u, '');
    }

    /**
     * Checks whether the currently selected option is the correct answer.
     * @returns true if the selected option is correct, false otherwise
     */
    isCorrectAnswer(): boolean {
        const idx = this.selectedIndex();
        if (idx === undefined) {
            return false;
        }
        return this.mcqData().options[idx].correct;
    }
}
