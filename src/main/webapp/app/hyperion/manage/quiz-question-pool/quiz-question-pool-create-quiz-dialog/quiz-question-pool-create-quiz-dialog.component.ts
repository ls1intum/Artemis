import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { TumAetUiInputNumberComponent } from '@tumaet/ui-angular/input-number';
import { TumAetUiMultiSelectComponent } from '@tumaet/ui-angular/multi-select';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { GeneratedQuestionType } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';
import { CompetencyOption, PoolDifficulty, PoolLectureUnit, PoolSelectionCandidate, PoolSelectionRequest } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

export interface LabeledOption<T> {
    label: string;
    value: T;
}

/** Bounds and default of the number of questions one draw asks for, matching the server's validation. */
export const MIN_QUESTIONS_PER_DRAW = 1;
export const MAX_QUESTIONS_PER_DRAW = 100;
const DEFAULT_QUESTIONS_PER_DRAW = 10;

const DIFFICULTY_ORDER: PoolDifficulty[] = ['EASY', 'MEDIUM', 'HARD'];
const QUESTION_TYPE_ORDER: GeneratedQuestionType[] = ['single-choice', 'multiple-choice', 'true-false'];

/**
 * Lets a reviewer choose which questions to draw from the pool for a new quiz: the competencies, optionally the lecture
 * units, the number of questions and the question types and difficulties. Emits the draw request; the page performs the draw and
 * opens the quiz editor.
 */
@Component({
    selector: 'jhi-quiz-question-pool-create-quiz-dialog',
    templateUrl: './quiz-question-pool-create-quiz-dialog.component.html',
    styleUrl: './quiz-question-pool-create-quiz-dialog.component.scss',
    imports: [FormsModule, TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiInputNumberComponent, TumAetUiMultiSelectComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionPoolCreateQuizDialogComponent {
    private translateService = inject(TranslateService);

    visible = model.required<boolean>();
    competencies = input.required<CompetencyOption[]>();
    lectureUnits = input.required<PoolLectureUnit[]>();
    /** The questions a draw may take, counted per cell. */
    candidates = input.required<PoolSelectionCandidate[]>();
    initialCompetencyId = input<number>();
    busy = input(false);
    create = output<PoolSelectionRequest>();

    readonly minCount = MIN_QUESTIONS_PER_DRAW;
    readonly maxCount = MAX_QUESTIONS_PER_DRAW;

    readonly selectedCompetencyIds = signal<number[]>([]);
    readonly selectedLectureUnitIds = signal<number[]>([]);
    readonly count = signal(DEFAULT_QUESTIONS_PER_DRAW);
    readonly selectedTypes = signal<GeneratedQuestionType[]>([]);
    readonly selectedDifficulties = signal<PoolDifficulty[]>([]);

    readonly unitOptions = computed<LabeledOption<number>[]>(() => {
        const competencyIds = new Set(this.selectedCompetencyIds());
        const options = new Map<number, LabeledOption<number>>();
        for (const unit of this.lectureUnits()) {
            if (competencyIds.has(unit.competencyId) && !options.has(unit.lectureUnitId)) {
                const label = unit.lectureTitle ? `${unit.lectureUnitName} (${unit.lectureTitle})` : unit.lectureUnitName;
                options.set(unit.lectureUnitId, { value: unit.lectureUnitId, label });
            }
        }
        return [...options.values()];
    });
    readonly typeOptions = computed<LabeledOption<GeneratedQuestionType>[]>(() => {
        const present = new Set(this.candidates().map((candidate) => candidate.questionType));
        return QUESTION_TYPE_ORDER.filter((type) => present.has(type)).map((type) => ({
            value: type,
            label: this.translateService.instant(`artemisApp.hyperion.quizQuestionPool.createQuiz.types.${type}`),
        }));
    });
    readonly difficultyOptions = computed<LabeledOption<PoolDifficulty>[]>(() => {
        const present = new Set(this.candidates().map((candidate) => candidate.difficulty));
        return DIFFICULTY_ORDER.filter((difficulty) => present.has(difficulty)).map((difficulty) => ({
            value: difficulty,
            label: this.translateService.instant(`artemisApp.hyperion.quizQuestionPool.list.difficulty.${difficulty}`),
        }));
    });
    /** How many questions a draw may take under the current choices. */
    readonly availableCount = computed(() => {
        const competencyIds = new Set(this.selectedCompetencyIds());
        const unitIds = new Set(this.selectedLectureUnitIds());
        const types = new Set(this.selectedTypes());
        const difficulties = new Set(this.selectedDifficulties());
        return this.candidates()
            .filter(
                (candidate) =>
                    competencyIds.has(candidate.competencyId) &&
                    (unitIds.size === 0 || unitIds.has(candidate.lectureUnitId)) &&
                    (types.size === 0 || types.has(candidate.questionType)) &&
                    (difficulties.size === 0 || difficulties.has(candidate.difficulty)),
            )
            .reduce((sum, candidate) => sum + candidate.count, 0);
    });
    readonly isValid = computed(() => this.selectedCompetencyIds().length > 0 && this.count() >= MIN_QUESTIONS_PER_DRAW && this.count() <= MAX_QUESTIONS_PER_DRAW);

    constructor() {
        effect(() => {
            if (this.visible()) {
                untracked(() => this.reset());
            }
        });
    }

    /** Starts from the competency the page filters by, or from every competency, with all units, types and difficulties. */
    reset(): void {
        const initial = this.initialCompetencyId();
        const competencyIds = this.competencies().map((competency) => competency.id);
        this.selectedCompetencyIds.set(initial !== undefined && competencyIds.includes(initial) ? [initial] : competencyIds);
        this.selectedLectureUnitIds.set([]);
        this.selectedTypes.set([]);
        this.selectedDifficulties.set([]);
        this.count.set(DEFAULT_QUESTIONS_PER_DRAW);
    }

    /** Changes the competencies and drops lecture units that no longer belong to one of them. */
    setCompetencies(competencyIds: number[]): void {
        this.selectedCompetencyIds.set(competencyIds ?? []);
        const allowed = new Set(this.unitOptions().map((option) => option.value));
        this.selectedLectureUnitIds.update((unitIds) => unitIds.filter((unitId) => allowed.has(unitId)));
    }

    submit(): void {
        if (!this.isValid()) {
            return;
        }
        const unitIds = this.selectedLectureUnitIds();
        const types = this.selectedTypes();
        const difficulties = this.selectedDifficulties();
        this.create.emit({
            competencyIds: this.selectedCompetencyIds(),
            lectureUnitIds: unitIds.length ? unitIds : undefined,
            count: this.count(),
            questionTypes: types.length ? types : undefined,
            difficulties: difficulties.length ? difficulties : undefined,
        });
    }

    cancel(): void {
        this.visible.set(false);
    }
}
