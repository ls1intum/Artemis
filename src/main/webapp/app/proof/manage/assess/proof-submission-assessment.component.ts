import { Component, OnInit, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BlockDefinitionModel } from 'app/proof/shared/entities/block-definition.model';
import { mathNodesEqual } from 'app/proof/shared/entities/math-node.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MathNodeLatexPipe } from 'app/proof/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';

@Component({
    selector: 'jhi-proof-submission-assessment',
    templateUrl: './proof-submission-assessment.component.html',
    imports: [AssessmentLayoutComponent, TranslateDirective, HtmlForMarkdownPipe, MathNodeLatexPipe, NgClass, ArtemisDatePipe],
})
export class ProofSubmissionAssessmentComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private blockRegistryService = inject(ProofBlockRegistryService);

    participation: StudentParticipation;
    proofExercise: ProofExercise;
    submission: ProofSubmission;
    result?: Result;

    isLoading = true;
    blocks = signal<BlockDefinitionModel[]>([]);

    ngOnInit() {
        this.route.data.subscribe(({ studentParticipation }) => {
            if (studentParticipation) {
                this.participation = studentParticipation;
                this.proofExercise = this.participation.exercise as ProofExercise;
                this.submission = this.participation.submissions![0] as ProofSubmission;
                this.result = this.submission.results?.[0];
                this.isLoading = false;
            }
        });

        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });
    }

    get hasAstExpressions(): boolean {
        return !!(this.proofExercise?.sourceExpression && this.proofExercise?.targetExpression);
    }

    get isProofComplete(): boolean {
        const steps = this.submission?.steps;
        if (!steps?.length || !this.proofExercise?.targetExpression) return false;
        return mathNodesEqual(steps[steps.length - 1].resultExpression, this.proofExercise.targetExpression);
    }

    getRuleName(ruleId: string): string {
        for (const block of this.blocks()) {
            const rule = block.rules?.find((r) => r.id === ruleId);
            if (rule) return rule.name;
        }
        return ruleId;
    }
}
