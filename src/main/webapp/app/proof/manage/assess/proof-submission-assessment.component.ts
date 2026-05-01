import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-proof-submission-assessment',
    templateUrl: './proof-submission-assessment.component.html',
    imports: [AssessmentLayoutComponent, TranslateDirective, HtmlForMarkdownPipe],
})
export class ProofSubmissionAssessmentComponent implements OnInit {
    private route = inject(ActivatedRoute);

    participation: StudentParticipation;
    proofExercise: ProofExercise;
    submission: ProofSubmission;
    result?: Result;

    isLoading = true;

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
    }
}
