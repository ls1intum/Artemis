import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { StudentVotesAction, StudentVotesActionName } from 'app/overview/student-questions/student-votes/student-votes.component';

export interface StudentQuestionAction {
    name: QuestionActionName;
    studentQuestion: StudentQuestion;
}

export enum QuestionActionName {
    DELETE,
    EXPAND,
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-student-question',
    templateUrl: './student-question.component.html',
    styleUrls: ['./../student-questions.scss'],
})
export class StudentQuestionComponent implements OnInit {
    @Input() studentQuestion: StudentQuestion;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactQuestion = new EventEmitter<StudentQuestionAction>();
    editText?: string;
    isEditMode: boolean;
    EditorMode = EditorMode;
    courseId: number;

    // Only allow certain html tags and no attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p'];
    allowedHtmlAttributes: string[] = ['href'];

    constructor(private studentQuestionService: StudentQuestionService, private route: ActivatedRoute) {}

    /**
     * checks if the user is the author of the question
     * sets the question text as the editor text
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.editText = this.studentQuestion.questionText;
    }

    /**
     * pass the studentQuestion to the row to delete
     */
    deleteQuestion(): void {
        this.interactQuestion.emit({
            name: QuestionActionName.DELETE,
            studentQuestion: this.studentQuestion,
        });
    }

    /**
     * Changes the question text
     */
    saveQuestion(): void {
        this.studentQuestion.questionText = this.editText;
        this.studentQuestionService.update(this.courseId, this.studentQuestion).subscribe(() => {
            this.isEditMode = false;
        });
    }

    /**
     * toggles the edit Mode
     * set the editor text to the question text
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.editText = this.studentQuestion.questionText;
    }

    /**
     * interact with actions sent from studentVotes
     * @param {StudentVotesAction} action
     */
    interactVotes(action: StudentVotesAction): void {
        switch (action.name) {
            case StudentVotesActionName.VOTE_CHANGE:
                this.updateVotes(action.value);
                break;
        }
    }

    /**
     * update the number of votes for this studentQuestion
     * @param {number} voteChange
     */
    updateVotes(voteChange: number): void {
        this.studentQuestionService.updateVotes(this.courseId, this.studentQuestion.id!, voteChange).subscribe((res) => {
            this.studentQuestion = res.body!;
            this.interactQuestion.emit({
                name: QuestionActionName.VOTE_CHANGE,
                studentQuestion: this.studentQuestion,
            });
        });
    }

    /**
     * Takes a studentQuestion and determines if the user is the author of it
     * @param {StudentQuestion} studentQuestion
     * @returns {boolean}
     */
    isAuthorOfQuestion(studentQuestion: StudentQuestion): boolean {
        return this.user ? studentQuestion.author!.id === this.user.id : false;
    }
}
