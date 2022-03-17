import { Component, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { firstValueFrom, Subject } from 'rxjs';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { HttpResponse } from '@angular/common/http';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-plagiarism-cases-review',
    templateUrl: './plagiarism-cases-review.component.html',
    // since this is the only style we need, there's no need for another file.
    styles: [':host ::ng-deep .gutter{background: none !important;}'],
})
export class PlagiarismCasesReviewComponent implements OnInit {
    loading = true;
    exercise: Exercise;
    comparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    courseId: number;
    comparisonId: number;
    studentLogin: string;
    isStudentA: boolean;
    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
    studentStatement: string;
    instructorStatement?: string;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute, private accountService: AccountService) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.comparisonId = Number(params['plagiarismComparisonId']);
            this.plagiarismCasesService.getPlagiarismComparisonForStudent(this.courseId, this.comparisonId).subscribe((resp: HttpResponse<PlagiarismCase>) => {
                this.exercise = resp.body!.exercise;
                // this should always contain exactly one comparison
                this.comparison = resp.body!.comparisons[0];
                if (this.comparison.instructorStatementA) {
                    this.isStudentA = true;
                    this.instructorStatement = this.comparison.instructorStatementA;
                    this.studentStatement = this.comparison.studentStatementA ? this.comparison.studentStatementA : '';
                } else if (this.comparison.instructorStatementB) {
                    this.isStudentA = false;
                    this.instructorStatement = this.comparison.instructorStatementB;
                    this.studentStatement = this.comparison.studentStatementB ? this.comparison.studentStatementB : '';
                }
                this.loading = false;
            });
            this.accountService.identity().then((user: User) => {
                this.studentLogin = user!.login!;
            });
        });
    }

    canSaveStudentStatement(): boolean {
        return (this.isStudentA && !this.comparison.studentStatementA) || (!this.isStudentA && !this.comparison.studentStatementB);
    }

    saveStudentStatement(): void {
        if (this.isStudentA) {
            this.comparison.studentStatementA = this.studentStatement;
        } else {
            this.comparison.studentStatementB = this.studentStatement;
        }
        firstValueFrom(this.plagiarismCasesService.saveStudentStatement(this.courseId, this.comparisonId, this.studentStatement)).catch(() => {
            this.comparison.studentStatementA = undefined;
            this.comparison.studentStatementB = undefined;
        });
    }

    isConfirmed(): boolean {
        if (this.isStudentA) {
            return this.comparison.statusA && this.comparison.statusA === PlagiarismStatus.CONFIRMED;
        }
        return this.comparison.statusB && this.comparison.statusB === PlagiarismStatus.CONFIRMED;
    }

    hasStatus(): boolean {
        if (this.isStudentA) {
            return this.comparison.statusA && this.comparison.statusA !== PlagiarismStatus.NONE;
        }
        return this.comparison.statusB && this.comparison.statusB !== PlagiarismStatus.NONE;
    }
}
