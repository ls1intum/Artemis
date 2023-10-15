import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { faArrowLeft, faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-termination-confirmation',
    templateUrl: './exam-termination-confirmation.component.html',
    styleUrls: ['./exam-termination-confirmation.component.scss'],
})
export class ExamTerminationConfirmationComponent implements OnChanges {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() abandonInProgress = false;
    @Output() onExamAbandoned: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamContinue = new EventEmitter<void>();
    course?: Course;
    confirmed: boolean;

    testRun?: boolean;
    testExam?: boolean;

    accountName = '';
    enteredName = '';

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;

    constructor(
        private courseService: CourseManagementService,
        private artemisMarkdown: ArtemisMarkdownService,
        private accountService: AccountService,
        private examParticipationService: ExamParticipationService,
        private serverDateService: ArtemisServerDateService,
    ) {}

    /**
     * on changes uses the correct information to display in either start or final view
     * changes in the exam and subscription is handled in the exam-participation.component
     * if the student exam changes, we need to update the displayed times
     */
    ngOnChanges(): void {
        this.confirmed = false;
        this.testRun = this.studentExam.testRun;
        this.testExam = this.exam.testExam;

        this.accountService.identity().then((user) => {
            if (user && user.name) {
                this.accountName = user.name;
            }
        });
    }

    /**
     * Submits the exam
     */
    abandonExam() {
        this.onExamAbandoned.emit();
    }

    /**
     * Notify the parent component that the user wants to continue after hand in early
     */
    continue() {
        this.onExamContinue.emit();
    }

    get nameIsCorrect(): boolean {
        return this.enteredName.trim() === this.accountName.trim();
    }

    get inserted(): boolean {
        return this.enteredName.trim() !== '';
    }
}
