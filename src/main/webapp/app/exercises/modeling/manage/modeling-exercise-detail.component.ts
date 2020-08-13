import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService, ModelingSubmissionComparisonDTO } from './modeling-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AlertService } from 'app/core/alert/alert.service';
import { downloadFile } from 'app/shared/util/download.util';

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
    checkPlagiarismInProgress: boolean;

    constructor(
        private eventManager: JhiEventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private jhiAlertService: AlertService,
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

    checkPlagiarism() {
        this.checkPlagiarismInProgress = true;
        this.modelingExerciseService.checkPlagiarism(this.modelingExercise.id).subscribe(this.handleCheckPlagiarismResponse, () => {
            this.checkPlagiarismInProgress = false;
        });
    }

    handleCheckPlagiarismResponse = (response: HttpResponse<Array<ModelingSubmissionComparisonDTO>>) => {
        this.jhiAlertService.success('artemisApp.programmingExercise.checkPlagiarismSuccess');
        this.checkPlagiarismInProgress = false;
        const json = JSON.stringify(response.body);
        const blob = new Blob([json], { type: 'application/json' });
        downloadFile(blob, `check-plagiarism-modeling-exercise-${this.modelingExercise.id}.json`);
    };

    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', () => this.load(this.modelingExercise.id));
    }
}
