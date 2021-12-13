import { Component, Input } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismCasesService, StatementEntityResponseType } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

@Component({
    selector: 'jhi-plagiarism-cases-list',
    templateUrl: './plagiarism-cases-list.component.html',
    // since this is the only style we need, there's no need for another file.
    styles: [':host ::ng-deep .gutter{background: none!important;}'],
})
export class PlagiarismCasesListComponent {
    @Input() courseId: number;
    @Input() plagiarismCase: PlagiarismCase;
    @Input() hideFinished: boolean;
    instructorStatement = '';
    activeComparisonId?: number;
    activeSplitViewComparison = -1;
    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
    activeStudentLogin?: string;

    constructor(private plagiarismCasesService: PlagiarismCasesService) {}

    /**
     * checks if there is an instructorStatement for student A
     * @param comparisonIndex
     */
    hasInstructorStatementA(comparisonIndex: number): boolean {
        return !!this.plagiarismCase.comparisons[comparisonIndex].instructorStatementA;
    }

    /**
     * checks if there is an instructorStatement for student B
     * @param comparisonIndex
     */
    hasInstructorStatementB(comparisonIndex: number): boolean {
        return !!this.plagiarismCase.comparisons[comparisonIndex].instructorStatementB;
    }

    /**
     * hide the form to create a new instructorStatement
     */
    hideInstructorStatementForm(): void {
        this.activeStudentLogin = undefined;
        this.activeComparisonId = undefined;
    }

    /**
     * show the form to create a new instructorStatement for a comparison for a specific student
     * @param studentLogin
     * @param comparisonId
     */
    showInstructorStatementForm(studentLogin: string, comparisonId: number) {
        this.activeStudentLogin = studentLogin;
        this.activeComparisonId = comparisonId;
    }

    /**
     * show the details of the given comparison
     * @param comparisonId
     */
    showComparison(comparisonId: number): void {
        this.activeSplitViewComparison = comparisonId;
    }

    /**
     * save the instructorStatement for a student
     * @param student
     * @param i
     */
    saveInstructorStatement(student: string, i: number) {
        let studentLogin = '';
        switch (student) {
            case 'A':
                studentLogin = this.plagiarismCase.comparisons[i].submissionA.studentLogin;
                break;
            case 'B':
                studentLogin = this.plagiarismCase.comparisons[i].submissionB.studentLogin;
                break;
        }

        this.plagiarismCasesService
            .saveInstructorStatement(this.courseId, this.plagiarismCase.comparisons[i].id, studentLogin, this.instructorStatement)
            .subscribe((resp: StatementEntityResponseType) => {
                if (student === 'A') {
                    this.plagiarismCase.comparisons[i].instructorStatementA = resp.body!;
                } else if (student === 'B') {
                    this.plagiarismCase.comparisons[i].instructorStatementB = resp.body!;
                }
            });
    }

    /**
     * update the final status of a comparison for a student
     * @param confirm
     * @param comparisonIndex
     * @param studentLogin
     */
    updateStatus(confirm: boolean, comparisonIndex: number, studentLogin: string) {
        this.plagiarismCasesService
            .updatePlagiarismComparisonFinalStatus(this.courseId, this.plagiarismCase.comparisons[comparisonIndex].id, confirm, studentLogin)
            .subscribe(() => {
                if (this.plagiarismCase.comparisons[comparisonIndex].submissionA.studentLogin === studentLogin) {
                    this.plagiarismCase.comparisons[comparisonIndex].statusA = confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED;
                } else {
                    this.plagiarismCase.comparisons[comparisonIndex].statusB = confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED;
                }
            });
    }
}
