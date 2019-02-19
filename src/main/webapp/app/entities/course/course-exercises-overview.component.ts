import {Component, OnInit} from '@angular/core';
import {Course} from 'app/entities/course/course.model';
import {CourseService} from 'app/entities/course/course.service';
import {QuizExercise} from 'app/entities/quiz-exercise/quiz-exercise.model';
import {ActivatedRoute} from '@angular/router';
import {TextExercise} from 'app/entities/text-exercise/text-exercise.model';
import {ProgrammingExercise} from 'app/entities/programming-exercise/programming-exercise.model';
import {ModelingExercise} from 'app/entities/modeling-exercise/modeling-exercise.model';
import {FileUploadExercise} from 'app/entities/file-upload-exercise/file-upload-exercise.model';


@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-exercises-overview.component.html'
})
export class CourseExercisesOverviewComponent implements OnInit {

    quizExercises: QuizExercise[];
    textExercises: TextExercise[];
    programmingExercises: ProgrammingExercise[];
    modelingExercises: ModelingExercise[];
    fileUploadExercises: FileUploadExercise[];
    course: Course;
    courseId: number;

    constructor(
        private courseService: CourseService,
        private route: ActivatedRoute,
    ) {
    }

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.load();
    }

    get exercisesReady(): boolean {
        return [this.quizExercises, this.textExercises, this.programmingExercises, this.modelingExercises, this.fileUploadExercises].every(x => x instanceof Array);
    }

    load() {
        this.courseService.find(this.courseId).subscribe(courseResponse => {
            this.course = courseResponse.body;

            this.quizExercises = [];
            this.textExercises = [];
            this.programmingExercises = [];
            this.modelingExercises = [];
            this.fileUploadExercises = [];

        });
    }
}
