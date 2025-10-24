// src/main/webapp/app/quiz/manage/ai-quiz-generation-modal/ai-quiz-generation-modal.component.ts
import { Component, Input, inject } from '@angular/core';
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

    // expose enums to the template
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
        competencyIds: [],
    };

    loading = false;
    warnings: string[] = [];
    generated: AiGeneratedQuestionDTO[] = [];
    selected: Record<number, boolean> = {};

    private readonly activeModal = inject(NgbActiveModal);
    private readonly service = inject(AiQuizGenerationService);

    private readonly subtypeKeyMap: Record<AiRequestedSubtype | string, string> = {
        [AiRequestedSubtype.SINGLE_CORRECT]: 'artemisApp.quizExercise.aiGeneration.subtype.single',
        [AiRequestedSubtype.MULTI_CORRECT]: 'artemisApp.quizExercise.aiGeneration.subtypes.multiple',
        [AiRequestedSubtype.TRUE_FALSE]: 'artemisApp.quizExercise.aiGeneration.subtypes.trueFalse',
    };
    subtypeLabelKey(subtype: AiRequestedSubtype | string): string {
        return this.subtypeKeyMap[subtype] ?? 'artemisApp.quizExercise.aiGeneration.subtype.trueFalse';
    }
    cancel(): void {
        this.activeModal.dismiss();
    }

    generate(f: NgForm): void {
        if (!f.valid || !this.courseId) return;
        this.loading = true;
        this.warnings = [];
        this.generated = [];
        this.selected = {};

        this.service.generate(this.courseId, this.formData).subscribe((res: AiQuizGenerationResponse) => {
            this.loading = false;
            this.generated = res.questions ?? [];
            this.warnings = res.warnings ?? [];
            this.generated.forEach((_, i) => (this.selected[i] = true)); // preselect all
        });
    }

    useInEditor(): void {
        const picked = this.generated.filter((_, i) => this.selected[i]);
        this.activeModal.close({ questions: picked });
    }

    get anySelected(): boolean {
        return this.generated.some((_, i) => this.selected[i]);
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
