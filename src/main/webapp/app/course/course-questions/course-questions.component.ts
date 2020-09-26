import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { StudentQuestion } from 'app/entities/student-question.model';

@Component({
    selector: 'jhi-course-questions',
    templateUrl: './course-questions.component.html',
})
export class CourseQuestionsComponent implements OnInit, OnDestroy {
    courseId: number;
    studentQuestions: StudentQuestion[];

    paramSub: Subscription;

    constructor(private route: ActivatedRoute, private studentQuestionsService: StudentQuestionService) {}

    /**
     * On init fetch the course
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.studentQuestionsService.findQuestionsForCourse(this.courseId).subscribe((res) => {
                console.log(res);
                this.studentQuestions = res.body!;
            });
        });
    }

    getNumberOfApprovedAnswers(studentQuestion: StudentQuestion): number {
        return studentQuestion.answers.filter((question) => question.tutorApproved).length;
    }

    /**
     * On destroy unsubscribe.
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}
