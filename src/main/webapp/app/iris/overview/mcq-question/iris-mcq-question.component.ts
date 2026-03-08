import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { McqData } from 'app/iris/shared/entities/iris-content-type.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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

    selectOption(index: number): void {
        if (this.submitted()) {
            return;
        }
        this.selectedIndex.set(index);
    }

    submit(): void {
        if (this.selectedIndex() === undefined || this.submitted()) {
            return;
        }
        this.submitted.set(true);
    }

    optionLabel(index: number): string {
        return String.fromCharCode(65 + index);
    }

    isCorrectAnswer(): boolean {
        const idx = this.selectedIndex();
        if (idx === undefined) {
            return false;
        }
        return this.mcqData().options[idx].correct;
    }
}
