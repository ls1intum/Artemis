import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course/course.model';
import { CourseExerciseService, CourseService } from 'app/entities/course/course.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise } from 'app/entities/quiz-exercise/quiz-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { QuizExerciseService } from 'app/entities/quiz-exercise/quiz-exercise.service';
import { JhiAlertService } from 'ng-jhipster';
import { TextExercise } from 'app/entities/text-exercise/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise/modeling-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { Exercise } from 'app/entities/exercise';

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
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private quizExerciseService: QuizExerciseService
    ) {}

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

            this.quizExerciseService.findForCourse(this.courseId)
                .subscribe(
                    (res: HttpResponse<QuizExercise[]>) => {
                        this.quizExercises = res.body;
                        // reconnect exercise with course
                        this.quizExercises.forEach(quizExercises => {
                            quizExercises.course = this.course;
                        });
                    },
                    (res: HttpErrorResponse) => this.onError(res)
                );

            this.courseExerciseService.findAllTextExercisesForCourse(this.courseId)
                .subscribe(
                    (res: HttpResponse<TextExercise[]>) => {
                        this.textExercises = res.body;
                        // reconnect exercise with course
                        this.textExercises.forEach(textExercise => {
                            textExercise.course = this.course;
                        });
                    },
                    (res: HttpErrorResponse) => this.onError(res)
                );

            this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId)
                .subscribe(
                    (res: HttpResponse<ProgrammingExercise[]>) => {
                        this.programmingExercises = res.body;
                        // reconnect exercise with course
                        this.programmingExercises.forEach(programmingExercise => {
                            programmingExercise.course = this.course;
                        });
                    },
                    (res: HttpErrorResponse) => this.onError(res)
                );

            this.courseExerciseService.findAllModelingExercisesForCourse(this.courseId)
                .subscribe(
                    (res: HttpResponse<ModelingExercise[]>) => {
                        this.modelingExercises = res.body;
                        // reconnect exercise with course
                        this.modelingExercises.forEach(modelingExercises => {
                            modelingExercises.course = this.course;
                        });
                    },
                    (res: HttpErrorResponse) => this.onError(res)
                );

            this.courseExerciseService.findAllFileUploadExercisesForCourse(this.courseId)
                .subscribe(
                    (res: HttpResponse<FileUploadExercise[]>) => {
                        this.fileUploadExercises = res.body;
                        // reconnect exercise with course
                        this.fileUploadExercises.forEach(fileUploadExercise => {
                            fileUploadExercise.course = this.course;
                        });
                    },
                    (res: HttpErrorResponse) => this.onError(res)
                );
        });
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }
}
