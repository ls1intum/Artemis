import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';
import { Course } from '../course/course.model';
import { CourseService } from '../course/course.service';
import { QuizExercise } from './quiz-exercise.model';
import { DragAndDropQuestionUtil } from '../../components/util/drag-and-drop-question-util.service';
import { NG1TRANSLATE_SERVICE } from '../../core/language/ng1-translate.service';
import { NG1TRANSLATEPARTIALLOADER_SERVICE } from '../../core/language/ng1-translate-partial-loader.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { QuestionType } from '../../entities/question';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    template: `<div><quiz-exercise-detail
            [course]="course"
            [quizExercise]="quizExercise"
            [repository]="repository"
            [courseRepository]="courseRepository"
            [dragAndDropQuestionUtil]="dragAndDropQuestionUtil"
            [router]="router"
            [translateService]="translateService"
            [fileUploaderService]="fileUploaderService">
    </quiz-exercise-detail></div>`,
    providers: [DragAndDropQuestionUtil]
})
export class QuizExerciseDetailComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    /** Dependencies as defined by the upgraded component */
    course: Course;
    quizExercise: QuizExercise;
    paramSub: Subscription;
    repository: QuizExerciseService;
    courseRepository: CourseService;
    dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    router: Router;
    translateService: TranslateService;
    fileUploaderService: FileUploaderService;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseService,
        private quizExerciseService: QuizExerciseService,
        dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        router: Router,
        translateService: TranslateService,
        fileUploaderService: FileUploaderService,
        @Inject(NG1TRANSLATE_SERVICE) private $translate: any,
        @Inject(NG1TRANSLATEPARTIALLOADER_SERVICE) private $translatePartialLoader: any
    ) {
        this.dragAndDropQuestionUtil = dragAndDropQuestionUtil;
        this.router = router;
        this.translateService = translateService;
        this.fileUploaderService = fileUploaderService;
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            /** Query the courseService for the participationId given by the params */
            this.courseService.find(params['courseId']).subscribe((response: HttpResponse<Course>) => {
                this.course = response.body;
            });
            if (params['id']) {
                this.quizExerciseService.find(params['id']).subscribe((response: HttpResponse<QuizExercise>) => {
                    this.quizExercise = response.body;
                });
            }
        });
        this.repository = this.quizExerciseService;
        this.courseRepository = this.courseService;
        this.$translatePartialLoader.addPart('quizExercise');
        this.$translatePartialLoader.addPart('global');
        this.$translate.refresh();
    }

    ngOnDestroy() {
        /** Unsubscribe onDestroy */
        this.paramSub.unsubscribe();
    }
}
