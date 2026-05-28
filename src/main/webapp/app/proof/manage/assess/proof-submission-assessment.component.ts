import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BlockDefinitionModel } from 'app/proof/shared/entities/block-definition.model';
import { DerivationStep } from 'app/proof/shared/entities/derivation-step.model';
import { mathNodesEqual } from 'app/proof/shared/entities/math-node.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MathNodeLatexPipe } from 'app/proof/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-proof-submission-assessment',
    templateUrl: './proof-submission-assessment.component.html',
    imports: [
        AssessmentLayoutComponent,
        TranslateDirective,
        HtmlForMarkdownPipe,
        MathNodeLatexPipe,
        NgClass,
        ArtemisDatePipe,
        FormsModule,
        ButtonModule,
        CardModule,
        InputGroupModule,
        InputGroupAddonModule,
        InputTextModule,
        TagModule,
    ],
})
export class ProofSubmissionAssessmentComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private blockRegistryService = inject(ProofBlockRegistryService);
    private proofSubmissionService = inject(ProofSubmissionService);

    proofExercise: ProofExercise;
    submission: ProofSubmission;
    result?: Result;
    courseId = -1;

    isLoading = true;
    saveBusy = false;
    manualScore: number | undefined;
    saveSuccess = false;
    blocks = signal<BlockDefinitionModel[]>([]);

    ngOnInit() {
        this.route.data.subscribe(({ proofSubmission }) => {
            if (proofSubmission) {
                this.submission = proofSubmission as ProofSubmission;
                this.proofExercise = this.submission.participation?.exercise as ProofExercise;
                this.result = this.submission.results?.[this.submission.results.length - 1];
                this.isLoading = false;
            }
        });

        // Walk up the route tree to find courseId
        let snapshot = this.route.snapshot;
        while (snapshot) {
            if (snapshot.params['courseId']) {
                this.courseId = Number(snapshot.params['courseId']);
                break;
            }
            snapshot = snapshot.parent!;
        }

        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });
    }

    saveManualScore(): void {
        if (this.manualScore == undefined || this.manualScore < 0 || this.manualScore > 100) return;
        this.saveBusy = true;
        this.saveSuccess = false;
        this.proofSubmissionService.saveManualResult(this.submission.id!, this.manualScore).subscribe({
            next: (updated) => {
                this.result = updated.results?.[updated.results.length - 1];
                this.saveBusy = false;
                this.saveSuccess = true;
            },
            error: () => {
                this.saveBusy = false;
            },
        });
    }

    get hasAstExpressions(): boolean {
        return !!(this.proofExercise?.sourceExpression && this.proofExercise?.targetExpression);
    }

    get hasExampleDerivations(): boolean {
        return !!this.proofExercise?.exampleDerivations?.length;
    }

    isExampleComplete(derivation: DerivationStep[]): boolean {
        if (!derivation?.length || !this.proofExercise?.targetExpression) return false;
        return mathNodesEqual(derivation[derivation.length - 1].resultExpression, this.proofExercise.targetExpression);
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
