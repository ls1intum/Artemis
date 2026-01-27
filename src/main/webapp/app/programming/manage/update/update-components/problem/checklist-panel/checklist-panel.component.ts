import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faChevronDown, faChevronRight, faExclamationTriangle, faInfoCircle, faMagic, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { AlertService } from 'app/shared/service/alert.service';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-checklist-panel',
    templateUrl: './checklist-panel.component.html',
    styleUrls: ['./checklist-panel.component.scss'],
    standalone: true,
    imports: [CommonModule, TranslateModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective],
})
export class ChecklistPanelComponent {
    private hyperionApiService = inject(HyperionProblemStatementApiService);
    private alertService = inject(AlertService);

    @Input() exercise: ProgrammingExercise;
    @Input() problemStatement: string;

    analysisResult = signal<ChecklistAnalysisResponse | undefined>(undefined);
    isLoading = signal<boolean>(false);

    sections = {
        learningGoals: true,
        difficulty: true,
        quality: true,
    };

    // Icons
    faCheckCircle = faCheckCircle;
    faExclamationTriangle = faExclamationTriangle;
    faInfoCircle = faInfoCircle;
    faSpinner = faSpinner;
    faMagic = faMagic;
    faChevronDown = faChevronDown;
    faChevronRight = faChevronRight;

    analyze() {
        if (!this.exercise || !this.exercise.id) {
            return;
        }

        this.isLoading.set(true);
        const request = {
            problemStatement: this.problemStatement,
            existingDifficulty: this.exercise.difficulty,
            existingLearningGoals: this.exercise.competencyLinks?.map((l: CompetencyExerciseLink) => l.competency?.title).filter((title): title is string => !!title),
        };

        this.hyperionApiService.analyzeChecklist(this.exercise.id!, request).subscribe({
            next: (res) => {
                this.analysisResult.set(res);
                this.isLoading.set(false);
            },
            error: (err: any) => {
                this.alertService.error('artemisApp.programmingExercise.checklist.analysisError');
                this.isLoading.set(false);
            },
        });
    }

    get invalidLearningGoals() {
        return this.analysisResult()?.inferredLearningGoals?.filter((g) => (g.confidence ?? 0) < 0.5) || [];
    }

    get validLearningGoals() {
        return this.analysisResult()?.inferredLearningGoals?.filter((g) => (g.confidence ?? 0) >= 0.5) || [];
    }
}
