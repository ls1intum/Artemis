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
import { ExportToCsv } from 'export-to-csv';
import { SERVER_API_URL } from 'app/app.constants';

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

    checkPlagiarismJson() {
        this.checkPlagiarism((data) => {
            const json = JSON.stringify(data);
            const blob = new Blob([json], { type: 'application/json' });
            downloadFile(blob, `check-plagiarism-modeling-exercise_${this.modelingExercise.id}.json`);
        });
    }

    checkPlagiarismCsv() {
        this.checkPlagiarism((data) => {
            if (data.length > 0) {
                const csvExporter = new ExportToCsv({
                    fieldSeparator: ';',
                    quoteStrings: '"',
                    decimalSeparator: 'locale',
                    showLabels: true,
                    title: `Plagiarism Check for Modeling Exercise ${this.modelingExercise.id}: ${this.modelingExercise.title}`,
                    filename: `check-plagiarism-modeling-exercise-${this.modelingExercise.id}-${this.modelingExercise.title}`,
                    useTextFile: false,
                    useBom: true,
                    headers: ['Similarity', 'Participant 1', 'Submission 1', 'Score 1', 'Size 1', 'Link 1', 'Participant 2', 'Submission 2', 'Score 2', 'Size 2', 'Link 2'],
                });

                const courseId = this.modelingExercise.course ? this.modelingExercise.course.id : this.modelingExercise.exerciseGroup?.exam?.course?.id;

                const baseUrl = SERVER_API_URL + '#/course-management/';

                const csvData = data.map((comparisonResult) => {
                    return Object.assign({
                        Similarity: comparisonResult.similarity,
                        'Participant 1': comparisonResult.element1.studentLogin,
                        'Submission 1': comparisonResult.element1.submissionId,
                        'Score 1': comparisonResult.element1.score,
                        'Size 1': comparisonResult.element1.size,
                        'Link 1':
                            baseUrl +
                            courseId +
                            '/modeling-exercises/' +
                            this.modelingExercise.id +
                            '/submissions/' +
                            comparisonResult.element1.submissionId +
                            '/assessment?optimal=false&hideBackButton=true',
                        'Participant 2': comparisonResult.element2.studentLogin,
                        'Submission 2': comparisonResult.element2.submissionId,
                        'Score 2': comparisonResult.element2.score,
                        'Size 2': comparisonResult.element2.size,
                        'Link 2':
                            baseUrl +
                            courseId +
                            '/modeling-exercises/' +
                            this.modelingExercise.id +
                            '/submissions/' +
                            comparisonResult.element2.submissionId +
                            '/assessment?optimal=false&hideBackButton=true',
                    });
                });

                csvExporter.generateCsv(csvData);
            }
        });
    }

    private checkPlagiarism(completionHandler: (data: Array<ModelingSubmissionComparisonDTO>) => void) {
        this.checkPlagiarismInProgress = true;
        this.modelingExerciseService.checkPlagiarism(this.modelingExercise.id).subscribe(
            (response: HttpResponse<Array<ModelingSubmissionComparisonDTO>>) => {
                this.checkPlagiarismInProgress = false;
                completionHandler(response.body!);
            },
            () => (this.checkPlagiarismInProgress = false),
        );
    }

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
