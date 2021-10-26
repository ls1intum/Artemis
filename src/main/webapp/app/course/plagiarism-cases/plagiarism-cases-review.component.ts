import { Component, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { Subject } from 'rxjs';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

@Component({
    selector: 'jhi-plagiarism-cases-review',
    templateUrl: './plagiarism-cases-review.component.html',
    // since this is the only style we need, there's no need for another file.
    styles: [':host ::ng-deep .gutter{background: none!important;}'],
})
export class PlagiarismCasesReviewComponent implements OnInit {
    loading = true;
    exercise: Exercise;
    comparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    comparisonId: number;
    isStudentA: boolean;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
    response: string;
    instructorMessage: string | undefined;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.comparisonId = Number(this.route.snapshot.paramMap.get('plagiarismComparisonId'));
        this.plagiarismCasesService
            .getAnonymousPlagiarismComparison(this.comparisonId)
            .toPromise()
            .then((resp) => {
                this.exercise = resp!.exercise;
                // this should always contain exactly one comparison
                this.comparison = resp!.comparisons[0];
                if (this.comparison.notificationA !== undefined) {
                    this.isStudentA = true;
                    this.instructorMessage = this.comparison.notificationA.text;
                    this.response = this.comparison.statementA ? this.comparison.statementA : '';
                } else if (this.comparison.notificationB !== undefined) {
                    this.isStudentA = false;
                    this.instructorMessage = this.comparison.notificationB.text;
                    this.response = this.comparison.statementB ? this.comparison.statementB : '';
                }
                this.loading = false;
            });
    }

    canSendStatement() {
        return (this.isStudentA && this.comparison.statementA === undefined) || (!this.isStudentA && this.comparison.statementB === undefined);
    }

    sendStatement() {
        if (this.isStudentA) {
            this.comparison.statementA = this.response;
        } else {
            this.comparison.statementB = this.response;
        }
        this.plagiarismCasesService
            .sendStatement(this.comparisonId, this.response)
            .toPromise()
            .catch(() => {
                this.comparison.statementA = undefined;
                this.comparison.statementB = undefined;
            });
    }

    isConfirmed() {
        if (this.isStudentA) {
            return this.comparison.statusA ? this.comparison.statusA === PlagiarismStatus.CONFIRMED : false;
        }
        return this.comparison.statusB ? this.comparison.statusB === PlagiarismStatus.CONFIRMED : false;
    }

    hasStatus() {
        if (this.isStudentA) {
            return this.comparison.statusA ? this.comparison.statusA !== PlagiarismStatus.NONE : false;
        }
        return this.comparison.statusB ? this.comparison.statusB !== PlagiarismStatus.NONE : false;
    }
}
