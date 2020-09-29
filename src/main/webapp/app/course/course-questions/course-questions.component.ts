import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { StudentQuestion } from 'app/entities/student-question.model';
import { SortService } from 'app/shared/service/sort.service';
import { Moment } from 'moment';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';

type StudentQuestionForOverview = {
    id: number;
    questionText: string | null;
    creationDate: Moment | null;
    votes: number;
    answers: number;
    approvedAnswers: number;
    exerciseOrLectureId: number;
    exerciseOrLectureTitle: string;
    belongsToExercise: boolean;
    exercise: Exercise;
    lecture: Lecture;
};

@Component({
    selector: 'jhi-course-questions',
    styles: ['.question-cell { max-width: 40vw; max-height: 120px; overflow: auto;}'],
    templateUrl: './course-questions.component.html',
})
export class CourseQuestionsComponent implements OnInit {
    courseId: number;
    studentQuestions: StudentQuestionForOverview[];
    studentQuestionsToDisplay: StudentQuestionForOverview[];

    showQuestionsWithApprovedAnswers = false;

    predicate = 'id';
    reverse = true;

    constructor(private route: ActivatedRoute, private studentQuestionsService: StudentQuestionService, private sortService: SortService) {}

    /**
     * On init fetch the course and the studentQuestions
     * convert studentQuestions to StudentQuestionForOverview type to allow sorting by all displayed fields
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.studentQuestionsService.findQuestionsForCourse(this.courseId).subscribe((res) => {
            this.studentQuestions = res.body!.map((question) => ({
                id: question.id,
                questionText: question.questionText,
                creationDate: question.creationDate,
                answers: question.answers ? question.answers.length : 0,
                approvedAnswers: this.getNumberOfApprovedAnswers(question),
                votes: question.votes,
                exerciseOrLectureId: question.exercise ? question.exercise.id : question.lecture.id,
                exerciseOrLectureTitle: question.exercise ? question.exercise.title : question.lecture.title,
                belongsToExercise: !!question.exercise,
                exercise: question.exercise,
                lecture: question.lecture,
            }));
            this.studentQuestionsToDisplay = this.studentQuestions.filter((question) => question.approvedAnswers === 0);
        });
    }

    /**
     * returns the number of approved answers for a question
     * @param { StudentQuestion }studentQuestion
     */
    getNumberOfApprovedAnswers(studentQuestion: StudentQuestion): number {
        return studentQuestion.answers ? studentQuestion.answers.filter((question) => question.tutorApproved).length : 0;
    }

    sortRows() {
        this.sortService.sortByProperty(this.studentQuestionsToDisplay, this.predicate, this.reverse);
    }

    hideQuestionsWithApprovedAnswers(): void {
        this.studentQuestionsToDisplay = this.studentQuestions.filter((question) => question.approvedAnswers === 0);
    }

    toggleHideQuestions(): void {
        if (!this.showQuestionsWithApprovedAnswers) {
            this.studentQuestionsToDisplay = this.studentQuestions;
        } else {
            this.hideQuestionsWithApprovedAnswers();
        }
        this.showQuestionsWithApprovedAnswers = !this.showQuestionsWithApprovedAnswers;
    }
}
