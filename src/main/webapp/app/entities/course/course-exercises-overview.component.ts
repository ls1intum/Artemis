import { Component, OnInit, ViewChildren } from '@angular/core';
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
import { QuizExerciseComponent } from 'app/entities/quiz-exercise';
import { ModelingExerciseComponent } from 'app/entities/modeling-exercise';
import { ProgrammingExerciseComponent } from 'app/entities/programming-exercise';
import { FileUploadExerciseComponent } from 'app/entities/file-upload-exercise';
import { TextExerciseComponent } from 'app/entities/text-exercise';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-exercises-overview.component.html'
})
export class CourseExercisesOverviewComponent implements OnInit {

    @ViewChildren(QuizExerciseComponent)
    quiz:QuizExerciseComponent;
    @ViewChildren(ModelingExerciseComponent)
    modeling:ModelingExerciseComponent;
    @ViewChildren(ProgrammingExerciseComponent)
    programming:ProgrammingExerciseComponent;
    @ViewChildren(FileUploadExerciseComponent)
    fileupload:FileUploadExerciseComponent;
    @ViewChildren(TextExerciseComponent)
    text:TextExerciseComponent;

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

    ngAfterViewInit(){
        this.quiz.loadForCourse(this.courseId);
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

    setQuizExercisesStatus() {
        this.quizExercises.forEach(quizExercise => (quizExercise.status = this.quizExerciseService.statusForQuiz(quizExercise)));
    }
}
