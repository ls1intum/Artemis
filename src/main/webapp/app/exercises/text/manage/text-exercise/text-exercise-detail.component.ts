import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { TextExercise } from 'app/entities/text-exercise.model';
import { SubmissionComparisonDTO, TextExerciseService } from './text-exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { downloadFile, downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { ExportToCsv } from 'export-to-csv';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html',
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {
    AssessmentType = AssessmentType;

    textExercise: TextExercise;
    isExamExercise: boolean;
    checkPlagiarismInProgress: boolean;

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
     * Returns the route for editing the exercise. Exam and course exercises have different routes.
     */
    getEditRoute() {
        if (this.isExamExercise) {
            return [
                '/course-management',
                this.textExercise.exerciseGroup?.exam?.course?.id,
                'exams',
                this.textExercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                this.textExercise.exerciseGroup?.id,
                'text-exercises',
                this.textExercise.id,
                'edit',
            ];
        } else {
            return ['/course-management', this.textExercise.course?.id, 'text-exercises', this.textExercise.id, 'edit'];
        }
    }

    /**
     * Go back.
     */
    previousState() {
        window.history.back();
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

    checkPlagiarismJson() {
        this.checkPlagiarism((data) => {
            const json = JSON.stringify(data);
            const blob = new Blob([json], { type: 'application/json' });
            downloadFile(blob, `check-plagiarism-text-exercise_${this.textExercise.id}.json`);
        });
    }

    checkPlagiarismJPlag() {
        this.checkPlagiarismInProgress = true;
        this.textExerciseService.checkPlagiarismJPlag(this.textExercise.id!).subscribe(
            (response: HttpResponse<Blob>) => {
                this.checkPlagiarismInProgress = false;
                downloadZipFileFromResponse(response);
            },
            () => (this.checkPlagiarismInProgress = false),
        );
    }

    checkPlagiarismCsv() {
        this.checkPlagiarism((data) => {
            if (data.length > 0) {
                const csvExporter = new ExportToCsv({
                    fieldSeparator: ';',
                    quoteStrings: '"',
                    decimalSeparator: 'locale',
                    showLabels: true,
                    title: `Plagiarism Check for Text Exercise ${this.textExercise.id}: ${this.textExercise.title}`,
                    filename: `check-plagiarism-textExercise_${this.textExercise.id}`,
                    useTextFile: false,
                    useBom: true,
                    headers: ['Student A', 'Submission A', 'Student B', 'Submission B', ...Object.keys(data[0].distanceMetrics)],
                });

                const fullname = (student: User): string => `${student.firstName || ''} ${student.lastName || ''}`.trim();
                const csvData = data.map((dto) => {
                    const submissionA = dto.submissions[0];
                    const submissionB = dto.submissions[1];
                    const studentA = (submissionA.participation as StudentParticipation).student!;
                    const studentB = (submissionB.participation as StudentParticipation).student!;
                    return Object.assign(
                        {
                            'Student A': fullname(studentA),
                            'Submission A': submissionA.text,
                            'Student B': fullname(studentB),
                            'Submission B': submissionB.text,
                        },
                        dto.distanceMetrics,
                    );
                });

                csvExporter.generateCsv(csvData);
            }
        });
    }

    private checkPlagiarism(completionHandler: (data: Array<SubmissionComparisonDTO>) => void) {
        this.checkPlagiarismInProgress = true;
        this.textExerciseService.checkPlagiarism(this.textExercise.id!).subscribe(
            (response: HttpResponse<Array<SubmissionComparisonDTO>>) => {
                this.checkPlagiarismInProgress = false;
                completionHandler(response.body!);
            },
            () => (this.checkPlagiarismInProgress = false),
        );
    }
}
