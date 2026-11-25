import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiInputNumberComponent, TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PoolSettings } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

/** Bounds of the question counts per lecture unit, matching the server's validation. */
const MIN_QUESTIONS_PER_LECTURE_UNIT = 1;
const MAX_QUESTIONS_PER_LECTURE_UNIT = 60;
/** Bounds of one difficulty share in percent. */
const MIN_PERCENT = 0;
const MAX_PERCENT = 100;
/** Bounds of the correct answer options of a multiple-choice question, matching the server's validation. */
const MIN_CORRECT_OPTIONS = 1;
const MAX_CORRECT_OPTIONS = 4;

/**
 * Lets an instructor edit the pool settings of a course in a dialog: the single-choice count per lecture unit, the optional
 * multiple-choice count with its range of correct options, the difficulty mix and the overnight builds. Edits stay in the dialog's
 * draft until it emits them to save.
 */
@Component({
    selector: 'jhi-quiz-question-pool-settings-dialog',
    templateUrl: './quiz-question-pool-settings-dialog.component.html',
    imports: [FormsModule, TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiInputNumberComponent, TumAetUiToggleSwitchComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionPoolSettingsDialogComponent {
    readonly visible = model.required<boolean>();
    readonly settings = input.required<PoolSettings>();
    readonly saving = input(false);
    readonly save = output<PoolSettings>();

    readonly minQuestionsPerLectureUnit = MIN_QUESTIONS_PER_LECTURE_UNIT;
    readonly maxQuestionsPerLectureUnit = MAX_QUESTIONS_PER_LECTURE_UNIT;
    readonly minPercent = MIN_PERCENT;
    readonly maxPercent = MAX_PERCENT;
    readonly minCorrectOptions = MIN_CORRECT_OPTIONS;
    readonly maxCorrectOptions = MAX_CORRECT_OPTIONS;

    /** The settings as edited in the dialog; starts from the stored settings each time the dialog opens. */
    readonly draft = linkedSignal<PoolSettings>(() => {
        this.visible();
        return deepClone(this.settings());
    });

    /** The difficulty shares added up; the draft is valid only when this is 100. */
    readonly difficultyPercentSum = computed(() => {
        const draft = this.draft();
        return draft.easyPercent + draft.mediumPercent + draft.hardPercent;
    });
    /** Whether the fewest correct options do not exceed the most. */
    readonly correctOptionRangeOrdered = computed(() => this.draft().multipleChoiceMinCorrectOptions <= this.draft().multipleChoiceMaxCorrectOptions);
    readonly isValid = computed(() => {
        const draft = this.draft();
        const counts = [draft.singleChoiceQuestionsPerLectureUnit, draft.multipleChoiceQuestionsPerLectureUnit];
        const shares = [draft.easyPercent, draft.mediumPercent, draft.hardPercent];
        const bounds = [draft.multipleChoiceMinCorrectOptions, draft.multipleChoiceMaxCorrectOptions];
        return (
            counts.every((count) => count >= MIN_QUESTIONS_PER_LECTURE_UNIT && count <= MAX_QUESTIONS_PER_LECTURE_UNIT) &&
            shares.every((share) => share >= MIN_PERCENT && share <= MAX_PERCENT) &&
            this.difficultyPercentSum() === 100 &&
            bounds.every((bound) => bound >= MIN_CORRECT_OPTIONS && bound <= MAX_CORRECT_OPTIONS) &&
            this.correctOptionRangeOrdered()
        );
    });

    setPoolGenerationEnabled(enabled: boolean): void {
        this.change({ poolGenerationEnabled: enabled });
    }

    setSingleChoiceQuestionsPerLectureUnit(questions: number | undefined): void {
        this.change({ singleChoiceQuestionsPerLectureUnit: questions ?? 0 });
    }

    setMultipleChoiceEnabled(enabled: boolean): void {
        this.change({ multipleChoiceEnabled: enabled });
    }

    setMultipleChoiceQuestionsPerLectureUnit(questions: number | undefined): void {
        this.change({ multipleChoiceQuestionsPerLectureUnit: questions ?? 0 });
    }

    setMultipleChoiceMinCorrectOptions(options: number | undefined): void {
        this.change({ multipleChoiceMinCorrectOptions: options ?? 0 });
    }

    setMultipleChoiceMaxCorrectOptions(options: number | undefined): void {
        this.change({ multipleChoiceMaxCorrectOptions: options ?? 0 });
    }

    setEasyPercent(percent: number | undefined): void {
        this.change({ easyPercent: percent ?? 0 });
    }

    setMediumPercent(percent: number | undefined): void {
        this.change({ mediumPercent: percent ?? 0 });
    }

    setHardPercent(percent: number | undefined): void {
        this.change({ hardPercent: percent ?? 0 });
    }

    submit(): void {
        if (this.isValid() && !this.saving()) {
            this.save.emit(this.draft());
        }
    }

    cancel(): void {
        this.visible.set(false);
    }

    private change(changes: Partial<PoolSettings>): void {
        this.draft.update((draft) => cloneWith(draft, changes));
    }
}
