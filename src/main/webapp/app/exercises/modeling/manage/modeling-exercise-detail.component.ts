import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html',
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {
    modelingExercise: ModelingExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    sampleSolution: SafeHtml;
    sampleSolutionUML: UMLModel;

    constructor(
        private eventManager: JhiEventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private jhiAlertService: JhiAlertService,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInModelingExercises();
    }

    load(id: number) {
        this.modelingExerciseService.find(id).subscribe((modelingExerciseResponse: HttpResponse<ModelingExercise>) => {
            this.modelingExercise = modelingExerciseResponse.body!;
            this.problemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.problemStatement);
            this.gradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.gradingInstructions);
            this.sampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.sampleSolutionExplanation);
            if (this.modelingExercise.sampleSolutionModel && this.modelingExercise.sampleSolutionModel !== '') {
                this.sampleSolutionUML = JSON.parse(this.modelingExercise.sampleSolutionModel);
            }
            if (this.modelingExercise.categories) {
                this.modelingExercise.categories = this.modelingExercise.categories.map((category) => JSON.parse(category));
            }
        });
    }

    downloadAsPDf() {
        const model = this.modelingExercise.sampleSolutionModel;
        if (model) {
            this.modelingExerciseService.convertToPdf(model).subscribe(
                (response: HttpResponse<String>) => {
                    console.log(response);
                },
                (err: string) => {
                    this.jhiAlertService.error('artemisApp.modelingExercise.apollonConversion.error');
                },
            );
        }
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', () => this.load(this.modelingExercise.id!));
    }
}
