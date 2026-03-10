import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { McqData } from 'app/iris/shared/entities/iris-content-type.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Interactive multiple-choice question component rendered inside Iris chat messages.
 * Displays a question with selectable options, handles submission, and shows feedback.
 */
@Component({
    selector: 'jhi-iris-mcq-question',
    standalone: true,
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './iris-mcq-question.component.html',
    styleUrl: './iris-mcq-question.component.scss',
})
export class IrisMcqQuestionComponent {
    mcqData = input.required<McqData>();

    selectedIndex = signal<number | undefined>(undefined);
    submitted = signal(false);

    /**
     * Selects an option by index. No-op if the question has already been submitted.
     * @param index the index of the option to select
     */
    selectOption(index: number): void {
        if (this.submitted()) {
            return;
        }
        this.selectedIndex.set(index);
    }

    /**
     * Submits the current selection and locks the question from further changes.
     */
    submit(): void {
        if (this.selectedIndex() === undefined || this.submitted()) {
            return;
        }
        this.submitted.set(true);
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
