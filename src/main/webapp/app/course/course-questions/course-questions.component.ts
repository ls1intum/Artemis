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
    studentQuestions: StudentQuestion[];

    paramSub: Subscription;

    constructor(private route: ActivatedRoute, private studentQuestionsService: StudentQuestionService) {}

    /**
     * On init fetch the course
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.studentQuestionsService.findQuestionsForCourse(params['courseId']).subscribe((res) => {
                console.log(res);
                this.studentQuestions = res.body!;
            });
        });
    }

    /**
     * On destroy unsubscribe.
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}
