import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { StudentQuestion } from 'app/entities/student-question.model';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-course-questions',
    styles: ['.question-cell { max-width: 40vw; max-height: 120px; overflow: auto;}'],
    templateUrl: './course-questions.component.html',
})
export class CourseQuestionsComponent implements OnInit {
    courseId: number;
    studentQuestions: StudentQuestion[];

    predicate = 'id';
    reverse = true;

    constructor(private route: ActivatedRoute, private studentQuestionsService: StudentQuestionService, private sortService: SortService) {}

    /**
     * On init fetch the course
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.studentQuestionsService.findQuestionsForCourse(this.courseId).subscribe((res) => {
            this.studentQuestions = res.body!;
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
        this.sortService.sortByProperty(this.studentQuestions, this.predicate, this.reverse);
    }
}
