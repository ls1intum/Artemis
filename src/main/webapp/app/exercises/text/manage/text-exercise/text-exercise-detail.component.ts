import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AssessmentType } from 'app/entities/assessment-type.model';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html',
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {
    AssessmentType = AssessmentType;

    textExercise: TextExercise;
    isExamExercise: boolean;
    formattedProblemStatement: SafeHtml | null;
    formattedSampleSolution: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private textExerciseService: TextExerciseService,
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
    ) {}

    /**
     * Loads the text exercise and subscribes to changes of it on component initialization.
     */
    ngOnInit() {
        // TODO: route determines whether the component is in exam mode
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInTextExercises();
    }

    /**
     * Requests the text exercise referenced by the given id.
     * @param id of the text exercise of type {number}
     */
    load(id: number) {
        // TODO: Use a separate find method for exam exercises containing course, exam, exerciseGroup and exercise id
        this.textExerciseService.find(id).subscribe((textExerciseResponse: HttpResponse<TextExercise>) => {
            this.textExercise = textExerciseResponse.body!;
            this.isExamExercise = !!this.textExercise.exerciseGroup;

            this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.gradingInstructions);
            this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.problemStatement);
            this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.textExercise.sampleSolution);
            if (this.textExercise.categories) {
                this.textExercise.categories = this.textExercise.categories.map((category) => JSON.parse(category));
            }
        });
    }

    /**
     * Unsubscribe from changes of text exercise on destruction of component.
     */
    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Subscribe to changes of the text exercise.
     */
    registerChangeInTextExercises() {
        this.eventSubscriber = this.eventManager.subscribe('textExerciseListModification', () => this.load(this.textExercise.id!));
    }
}
