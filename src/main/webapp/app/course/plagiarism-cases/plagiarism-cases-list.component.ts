import { Component, Input, OnInit } from '@angular/core';
import { PlagiarismCase } from 'app/course/plagiarism-cases/types/PlagiarismCase';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { Subject } from 'rxjs';
import { PlagiarismStatus } from "app/exercises/shared/plagiarism/types/PlagiarismStatus";

@Component({
    selector: 'jhi-plagiarism-cases-list',
    templateUrl: './plagiarism-cases-list.component.html',
    // since this is the only style we need, there's no need for another file.
    styles: [':host ::ng-deep .gutter{background: none!important;}'],
})
export class PlagiarismCasesListComponent implements OnInit {
    @Input() plagiarismCase: PlagiarismCase;
    @Input() hideFinished: boolean;
    notificationText = '';
    activeComparisonId: number | undefined = undefined;
    activeSplitViewComparison = -1;
    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
    activeStudentLogin: string | undefined;

    constructor(private plagiarismCasesService: PlagiarismCasesService) {
    }

    ngOnInit(): void {
    }

    isStudentANotified(comparisonIndex: number): boolean {
        return this.plagiarismCase.comparisons[comparisonIndex].notificationA !== undefined;
    }

    isStudentBNotified(comparisonIndex: number): boolean {
        return this.plagiarismCase.comparisons[comparisonIndex].notificationB !== undefined;
    }

    hideNotificationForm() {
        this.activeStudentLogin = undefined;
        this.activeComparisonId = undefined;
    }

    showNotificationForm(studentLogin: string, comparisonId: number) {
        this.activeStudentLogin = studentLogin;
        this.activeComparisonId = comparisonId;
    }

    showComparison(comparisonId: number): void {
        this.activeSplitViewComparison = comparisonId;
    }

    sendNotification(student: string, i: number) {
        let studentLogin = '';
        switch (student) {
            case 'A':
                studentLogin = this.plagiarismCase.comparisons[i].submissionA.studentLogin;
                break;
            case 'B':
                studentLogin = this.plagiarismCase.comparisons[i].submissionB.studentLogin;
                break;
        }

        this.plagiarismCasesService.sendPlagiarismNotification(studentLogin, this.plagiarismCase.comparisons[i].id, this.notificationText).subscribe((notification) => {
            if (student === 'A') {
                this.plagiarismCase.comparisons[i].notificationA = notification;
            } else if (student === 'B') {
                this.plagiarismCase.comparisons[i].notificationB = notification;
            }
        });
    }

    updateStatus(confirm: boolean, comparisonIndex: number, studentLogin: string) {
        this.plagiarismCasesService.updatePlagiarismStatus(confirm, this.plagiarismCase.comparisons[comparisonIndex].id, studentLogin).subscribe(() => {
            if (this.plagiarismCase.comparisons[comparisonIndex].submissionA.studentLogin === studentLogin) {
                this.plagiarismCase.comparisons[comparisonIndex].statusA = confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED;
            } else {
                this.plagiarismCase.comparisons[comparisonIndex].statusB = confirm ? PlagiarismStatus.CONFIRMED : PlagiarismStatus.DENIED;
            }
        });
    }
}
