import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { mathNodesEqual } from 'app/math/shared/entities/math-node.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-math-submission-assessment',
    templateUrl: './math-submission-assessment.component.html',
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
export class MathSubmissionAssessmentComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private blockRegistryService = inject(MathBlockRegistryService);
    private mathSubmissionService = inject(MathSubmissionService);

    mathExercise: MathExercise;
    submission: MathSubmission;
    result?: Result;
    courseId = -1;

    isLoading = true;
    saveBusy = false;
    manualScore: number | undefined;
    saveSuccess = false;
    blocks = signal<BlockDefinitionModel[]>([]);

    ngOnInit() {
        this.route.data.subscribe(({ mathSubmission }) => {
            if (mathSubmission) {
                this.submission = mathSubmission as MathSubmission;
                this.mathExercise = this.submission.participation?.exercise as MathExercise;
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
        this.mathSubmissionService.saveManualResult(this.submission.id!, this.manualScore).subscribe({
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
        return !!(this.mathExercise?.sourceExpression && this.mathExercise?.targetExpression);
    }

    get hasExampleDerivations(): boolean {
        return !!this.mathExercise?.exampleDerivations?.length;
    }

    isExampleComplete(derivation: DerivationStep[]): boolean {
        if (!derivation?.length || !this.mathExercise?.targetExpression) return false;
        return mathNodesEqual(derivation[derivation.length - 1].resultExpression, this.mathExercise.targetExpression);
    }

    get isMathComplete(): boolean {
        const steps = this.submission?.steps;
        if (!steps?.length || !this.mathExercise?.targetExpression) return false;
        return mathNodesEqual(steps[steps.length - 1].resultExpression, this.mathExercise.targetExpression);
    }

    getRuleName(ruleId: string): string {
        for (const block of this.blocks()) {
            const rule = block.rules?.find((r) => r.id === ruleId);
            if (rule) return rule.name;
        }
        return ruleId;
    }
}
