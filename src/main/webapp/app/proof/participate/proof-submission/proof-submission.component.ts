import { Component, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';

@Component({
    selector: 'jhi-proof-submission',
    templateUrl: './proof-submission.component.html',
    imports: [
        HeaderParticipationPageComponent,
        ButtonComponent,
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
        ExerciseSubmitButtonComponent,
    ],
})
export class ProofSubmissionComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private proofSubmissionService = inject(ProofSubmissionService);
    private alertService = inject(AlertService);

    participationId = input<number>();
    
    proofExercise: ProofExercise;
    participation: StudentParticipation;
    submission: ProofSubmission;
    result?: Result;
    
    isSaving = false;
    answer: string;
    studentCheckboxState: boolean;

    ngOnInit() {
        const participationIdParam = this.participationId() !== undefined ? this.participationId() : Number(this.route.snapshot.paramMap.get('participationId'));
        if (participationIdParam === undefined || Number.isNaN(participationIdParam)) {
            return this.alertService.error('artemisApp.proofExercise.error');
        }
        const participationId = participationIdParam!;

        this.proofSubmissionService.getDataForProofEditor(participationId).subscribe({
            next: (response) => {
                this.submission = response.body as ProofSubmission;
                this.participation = this.submission.participation as StudentParticipation;
                this.proofExercise = this.participation.exercise as ProofExercise;

                this.answer = this.submission.text ?? '';
                this.studentCheckboxState = this.submission.studentCheckboxState ?? false;

                const results = this.submission.results;
                if (results && results.length > 0) {
                    this.result = results[results.length - 1];
                }
            },
            error: () => this.alertService.error('artemisApp.proofExercise.error'),
        });
    }

    ngOnDestroy() {
    }

    save() {
        if (!this.submission || !this.proofExercise) {
            return;
        }
        this.isSaving = true;
        this.submission.text = this.answer;
        this.submission.studentCheckboxState = this.studentCheckboxState;
        
        const observable = this.submission.id 
            ? this.proofSubmissionService.update(this.submission, this.proofExercise.id!)
            : this.proofSubmissionService.create(this.submission, this.proofExercise.id!);

        observable.subscribe({
            next: (response) => {
                this.submission = response.body!;
                this.isSaving = false;
                this.alertService.success('artemisApp.proofExercise.saveSuccessful');
            },
            error: () => {
                this.isSaving = false;
                this.alertService.error('artemisApp.proofExercise.saveFailed');
            },
        });
    }

    submit() {
        if (!this.submission || !this.proofExercise) {
            return;
        }
        this.submission.text = this.answer;
        this.submission.studentCheckboxState = this.studentCheckboxState;
        this.submission.submitted = true;

        const observable = this.submission.id 
            ? this.proofSubmissionService.update(this.submission, this.proofExercise.id!)
            : this.proofSubmissionService.create(this.submission, this.proofExercise.id!);

        observable.subscribe({
            next: (response) => {
                this.submission = response.body!;
                this.alertService.success('artemisApp.proofExercise.submitSuccessful');
                
                if (this.submission.results && this.submission.results.length > 0) {
                    this.result = this.submission.results[0];
                }
            },
            error: () => {
                this.submission.submitted = false;
                this.alertService.error('artemisApp.proofExercise.submitFailed');
            },
        });
    }
}
