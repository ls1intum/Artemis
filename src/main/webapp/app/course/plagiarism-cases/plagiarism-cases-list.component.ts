import { Component, Input } from '@angular/core';
import { PlagiarismCasesService, StatementEntityResponseType } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';

@Component({
    selector: 'jhi-plagiarism-cases-list',
    templateUrl: './plagiarism-cases-list.component.html',
    // since this is the only style we need, there's no need for another file.
    styles: [':host ::ng-deep .gutter{background: none!important;}'],
})
export class PlagiarismCasesListComponent {
    @Input() courseId: number;
    @Input() plagiarismComparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
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
     */
    hasInstructorStatementA(): boolean {
        return !!this.plagiarismComparison.instructorStatementA;
    }

    /**
     * checks if there is an instructorStatement for student B
     */
    hasInstructorStatementB(): boolean {
        return !!this.plagiarismComparison.instructorStatementB;
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
     */
    saveInstructorStatement(student: string) {
        let studentLogin = '';
        switch (student) {
            case 'A':
                studentLogin = this.plagiarismComparison.submissionA.studentLogin;
                break;
            case 'B':
                studentLogin = this.plagiarismComparison.submissionB.studentLogin;
                break;
        }

        this.plagiarismCasesService
            .saveInstructorStatement(this.courseId, this.plagiarismComparison.id, studentLogin, this.instructorStatement)
            .subscribe((resp: StatementEntityResponseType) => {
                if (student === 'A') {
                    this.plagiarismComparison.instructorStatementA = resp.body!;
                } else if (student === 'B') {
                    this.plagiarismComparison.instructorStatementB = resp.body!;
                }
            });
    }

    /**
     * update the final status of a comparison for a student
     * @param confirm
     * @param studentLogin
     */
    updateStatus(confirm: boolean, studentLogin: string) {
        this.plagiarismCasesService.updatePlagiarismComparisonFinalStatus(this.courseId, this.plagiarismComparison.id, confirm, studentLogin).subscribe(() => {
            if (this.plagiarismComparison.submissionA.studentLogin === studentLogin) {
                this.plagiarismComparison.statusA = confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED;
            } else {
                this.plagiarismComparison.statusB = confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED;
            }
        });
    }
}
