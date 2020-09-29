import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { ModelingExerciseService, ModelingSubmissionComparisonDTO } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { downloadFile } from 'app/shared/util/download.util';
import { ExportToCsv } from 'export-to-csv';

@Component({
    selector: 'jhi-plagiarism-inspector',
    styleUrls: ['./plagiarism-inspector.component.scss'],
    templateUrl: './plagiarism-inspector.component.html',
})
export class PlagiarismInspectorComponent implements OnInit {
    selectedComparisonIndex: number;
    checkPlagiarismInProgress: boolean;
    modelingExercise: ModelingExercise;
    modelingSubmissionComparisons: Array<ModelingSubmissionComparisonDTO>;
    splitControlSubject: Subject<string> = new Subject<string>();

    private subscription: Subscription;

    constructor(private route: ActivatedRoute, private router: Router, private modelingExerciseService: ModelingExerciseService) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.modelingExerciseService.find(params['exerciseId']).subscribe((response: HttpResponse<ModelingExercise>) => {
                this.modelingExercise = response.body!;
            });
        });
    }

    handleTagPlagiarism(confirmed: boolean) {
        this.modelingSubmissionComparisons[this.selectedComparisonIndex].confirmed = confirmed;
    }

    checkPlagiarismJson() {
        const json = JSON.stringify(this.modelingSubmissionComparisons);
        const blob = new Blob([json], { type: 'application/json' });
        downloadFile(blob, `check-plagiarism-modeling-exercise_${this.modelingExercise.id}.json`);
    }

    checkPlagiarismCsv() {
        if (this.modelingSubmissionComparisons.length > 0) {
            const csvExporter = new ExportToCsv({
                fieldSeparator: ';',
                quoteStrings: '"',
                decimalSeparator: 'locale',
                showLabels: true,
                title: `Plagiarism Check for Modeling Exercise ${this.modelingExercise.id}: ${this.modelingExercise.title}`,
                filename: `check-plagiarism-modeling-exercise-${this.modelingExercise.id}-${this.modelingExercise.title}`,
                useTextFile: false,
                useBom: true,
                headers: [
                    'Similarity',
                    'Confirmed',
                    'Participant 1',
                    'Submission 1',
                    'Score 1',
                    'Size 1',
                    'Link 1',
                    'Participant 2',
                    'Submission 2',
                    'Score 2',
                    'Size 2',
                    'Link 2',
                ],
            });

            const courseId = this.modelingExercise.course ? this.modelingExercise.course.id : this.modelingExercise.exerciseGroup?.exam?.course?.id;

            const baseUrl = location.origin + '/#/course-management/';

            const csvData = this.modelingSubmissionComparisons.map((comparisonResult) => {
                return Object.assign({
                    Similarity: comparisonResult.similarity,
                    Confirmed: comparisonResult.confirmed ?? '',
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
    }

    checkPlagiarism() {
        this.checkPlagiarismInProgress = true;

        this.modelingExerciseService.checkPlagiarism(this.modelingExercise.id).subscribe(
            (response: HttpResponse<Array<ModelingSubmissionComparisonDTO>>) => {
                this.checkPlagiarismInProgress = false;
                this.modelingSubmissionComparisons = response.body!.sort((c1, c2) => c2.similarity - c1.similarity);
            },
            () => (this.checkPlagiarismInProgress = false),
        );
    }

    handleSplit(pane: string) {
        this.splitControlSubject.next(pane);
    }
}
