import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { NgbActiveModal, NgbAlertModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import {
    AiDifficultyLevel,
    AiGeneratedQuestionDTO,
    AiLanguage,
    AiQuizGenerationRequest,
    AiQuizGenerationResponse,
    AiQuizGenerationService,
    AiRequestedSubtype,
} from '../service/ai-quiz-generation.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';

@Component({
    standalone: true,
    selector: 'jhi-ai-quiz-generation-modal',
    templateUrl: './ai-quiz-generation-modal.component.html',
    imports: [CommonModule, FormsModule, NgbAlertModule, NgbTooltipModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
})
export class AiQuizGenerationModalComponent {
    @Input() courseId!: number;

    // expose enums to template
    AiLanguage = AiLanguage;
    AiDifficultyLevel = AiDifficultyLevel;
    AiRequestedSubtype = AiRequestedSubtype;
    faQuestionCircle = faQuestionCircle;

    formData: AiQuizGenerationRequest = {
        numberOfQuestions: 2,
        language: AiLanguage.ENGLISH,
        topic: '',
        promptHint: '',
        difficultyLevel: AiDifficultyLevel.MEDIUM,
        requestedSubtype: AiRequestedSubtype.SINGLE_CORRECT,
    };

    loading = signal(false);
    warnings = signal<string[]>([]);
    generated = signal<AiGeneratedQuestionDTO[]>([]);

    selected: Record<number, boolean> = {};

    private readonly activeModal = inject(NgbActiveModal);
    private readonly service = inject(AiQuizGenerationService);

    private readonly subtypeKeyMap: Record<AiRequestedSubtype | string, string> = {
        [AiRequestedSubtype.SINGLE_CORRECT]: 'artemisApp.quizExercise.aiGeneration.subtypes.single',
        [AiRequestedSubtype.MULTI_CORRECT]: 'artemisApp.quizExercise.aiGeneration.subtypes.multi',
        [AiRequestedSubtype.TRUE_FALSE]: 'artemisApp.quizExercise.aiGeneration.subtypes.trueFalse',
    };
    subtypeLabelKey(subtype: AiRequestedSubtype | string): string {
        return this.subtypeKeyMap[subtype] ?? 'artemisApp.quizExercise.aiGeneration.subtypes.single';
    }

    cancel(): void {
        this.activeModal.dismiss();
    }

    generate(f: NgForm): void {
        if (!f.valid || !this.courseId) {
            return;
        }

        this.loading.set(true);
        this.warnings.set([]);
        this.generated.set([]);
        this.selected = {};

        this.service.generate(this.courseId, this.formData).subscribe((res: AiQuizGenerationResponse) => {
            this.loading.set(false);
            const questions = res.questions ?? [];
            const warns = res.warnings ?? [];

            this.generated.set(questions);
            this.warnings.set(warns);

            questions.forEach((_, i) => (this.selected[i] = true)); // preselect all
        });
    }

    useInEditor(): void {
        const picked = this.generated().filter((_, i) => this.selected[i]);
        this.activeModal.close({
            questions: picked,
            requestedDifficulty: this.formData.difficultyLevel,
            requestedSubtype: this.formData.requestedSubtype,
        });
    }

    get anySelected(): boolean {
        return this.generated().some((_, i) => this.selected[i]);
    }

    difficultyToSlider(level: AiDifficultyLevel): number {
        switch (level) {
            case AiDifficultyLevel.EASY:
                return 0;
            case AiDifficultyLevel.HARD:
                return 2;
            default:
                return 1;
        }
    }

    sliderToDifficulty(value: number | string): AiDifficultyLevel {
        const v = Number(value);
        switch (v) {
            case 0:
                return AiDifficultyLevel.EASY;
            case 2:
                return AiDifficultyLevel.HARD;
            default:
                return AiDifficultyLevel.MEDIUM;
        }
    }
}
