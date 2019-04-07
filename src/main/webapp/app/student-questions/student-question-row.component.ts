import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { StudentQuestion, StudentQuestionService } from 'app/entities/student-question';
import { User } from 'app/core';

@Component({
    selector: 'jhi-student-question-row',
    templateUrl: './student-question-row.component.html',
    styleUrls: ['student-questions.scss'],
})
export class StudentQuestionRowComponent implements OnInit, OnDestroy {
    // @Input() exercise: Exercise;
    @Input() studentQuestion: StudentQuestion;
    @Input() selectedStudentQuestion: StudentQuestion;
    @Input() user: User;
    @Output() answerQuestion = new EventEmitter<StudentQuestion>();
    @Output() deleteQuestion = new EventEmitter<StudentQuestion>();
    @Output() editQuestion = new EventEmitter<StudentQuestion>();
    isExpanded: boolean;
    isQuestionAuthor: boolean;
    shortQuestionText: string;

    constructor(private studentQuestionService: StudentQuestionService) {}

    ngOnInit(): void {
        this.shortQuestionText = this.studentQuestion.questionText.substr(0, 25);
        if (this.studentQuestion.questionText.length > 25) {
            this.shortQuestionText += '...';
        }
        if (this.selectedStudentQuestion) {
            this.isExpanded = this.studentQuestion.id === this.selectedStudentQuestion.id;
        }
        if (this.user) {
            this.isQuestionAuthor = this.studentQuestion.author.id === this.user.id;
        }
    }

    ngOnDestroy(): void {}

    initAnswer(): void {
        this.answerQuestion.emit(this.studentQuestion);
    }

    initDelete() {
        this.deleteQuestion.emit(this.studentQuestion);
    }

    initEdit() {
        this.editQuestion.emit(this.studentQuestion);
    }

    addAnswer(): void {}
}
