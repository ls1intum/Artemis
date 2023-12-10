import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-termination-confirmation',
    templateUrl: './exam-termination-confirmation.component.html',
})
export class ExamTerminationConfirmationComponent implements OnChanges {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() abandonInProgress = false;
    @Output() onExamAbandoned: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamContinue = new EventEmitter<void>();
    course?: Course;
    confirmed: boolean;

    accountName = '';
    enteredName = '';

    // Icons
    faArrowLeft = faArrowLeft;

    constructor(private accountService: AccountService) {}

    ngOnChanges(): void {
        this.confirmed = false;

        this.accountService.identity().then((user) => {
            if (user && user.name) {
                this.accountName = user.name;
            }
        });
    }

    abandonExam() {
        this.onExamAbandoned.emit();
    }

    /**
     * Notify the parent component that the user wants to continue after abandon
     */
    continue() {
        this.onExamContinue.emit();
    }
}
