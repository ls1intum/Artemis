import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExercisePopupService, QuizExerciseService } from '../../entities/quiz-exercise';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { NG1TRANSLATE_SERVICE } from '../../core/language/ng1-translate.service';
import { NG1TRANSLATEPARTIALLOADER_SERVICE } from '../../core/language/ng1-translate-partial-loader.service';

@Component({
    selector: 'jhi-quiz-re-evaluate',
    template: `<div><quiz-re-evaluate
            [quizExercise]="quizExercise"
            [modalService]="modalService"
            [popupService]="popupService"
            [router]="router">
    </quiz-re-evaluate></div>`,
    providers: []
})
export class QuizReEvaluateComponent implements OnInit, OnDestroy {
    private subscription: Subscription;

    quizExercise: QuizExercise;
    modalService: NgbModal;
    popupService: QuizExercisePopupService;
    router: Router;

    constructor(
        private quizExerciseService: QuizExerciseService,
        private route: ActivatedRoute,
        private routerC: Router,
        private modalServiceC: NgbModal,
        private quizExercisePopupService: QuizExercisePopupService,
        @Inject(NG1TRANSLATE_SERVICE) private $translate: any,
        @Inject(NG1TRANSLATEPARTIALLOADER_SERVICE) private $translatePartialLoader: any
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.quizExerciseService.find(params['id']).subscribe(res => {
                this.quizExercise = res.body;
            });
        });

        this.modalService = this.modalServiceC;
        this.popupService = this.quizExercisePopupService;
        this.router = this.routerC;

        this.$translatePartialLoader.addPart('quizExercise');
        this.$translatePartialLoader.addPart('global');
        this.$translate.refresh();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }
}
