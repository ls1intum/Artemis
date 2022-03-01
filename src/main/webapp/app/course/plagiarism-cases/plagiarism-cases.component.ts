import { Component, OnInit } from '@angular/core';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { ActivatedRoute } from '@angular/router';
import { downloadFile } from 'app/shared/util/download.util';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { HttpResponse } from '@angular/common/http';
import { Exercise, getIcon } from 'app/entities/exercise.model';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';

@Component({
    selector: 'jhi-plagiarism-cases',
    templateUrl: './plagiarism-cases.component.html',
    styles: [],
})
export class PlagiarismCasesComponent implements OnInit {
    getIcon = getIcon;
    confirmedComparisons: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[] = [];
    exercises: Exercise[] = [];
    groupedComparisons: any;
    courseId: number;
    hideFinished = false;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.plagiarismCasesService
            .getConfirmedComparisons(this.courseId)
            .subscribe((resp: HttpResponse<PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[]>) => {
                this.confirmedComparisons = resp.body!;
                this.groupedComparisons = this.confirmedComparisons.reduce((acc, comparison) => {
                    // Group initialization
                    if (!acc[comparison.plagiarismResult!.exercise.id!]) {
                        acc[comparison.plagiarismResult!.exercise.id!] = [];
                        this.exercises.push(comparison.plagiarismResult!.exercise);
                    }

                    // Grouping
                    acc[comparison.plagiarismResult!.exercise.id!].push(comparison);

                    return acc;
                }, {});
            });
    }

    /**
     * export the plagiarism cases in CSV format
     */
    export(): void {
        const blobParts: string[] = ['Student login,Exercise,Similarity,Status\n'];
        this.confirmedComparisons.forEach((comparison) => {
            const exerciseTitleCSVSanitized = comparison.plagiarismResult?.exercise.title?.replace(',', '","');
            if (comparison.statusA === PlagiarismStatus.CONFIRMED) {
                blobParts.push(`${comparison.submissionA.studentLogin},${exerciseTitleCSVSanitized},${comparison.similarity},${comparison.statusA}\n`);
            } else if (!comparison.studentStatementA) {
                blobParts.push(`${comparison.submissionA.studentLogin},${exerciseTitleCSVSanitized},${comparison.similarity},No statement from student\n`);
            }
            if (comparison.statusB === PlagiarismStatus.CONFIRMED) {
                blobParts.push(`${comparison.submissionB.studentLogin},${exerciseTitleCSVSanitized},${comparison.similarity},${comparison.statusB}\n`);
            } else if (!comparison.studentStatementB) {
                blobParts.push(`${comparison.submissionB.studentLogin},${exerciseTitleCSVSanitized},${comparison.similarity},No statement from student\n`);
            }
        });
        downloadFile(new Blob(blobParts, { type: 'text/csv' }), 'plagiarism-cases.csv');
    }

    /**
     * calculate the total number of instructor statements
     */
    numberOfInstructorStatements(): number {
        let size = 0;
        this.confirmedComparisons.forEach((comp) => {
            if (comp.instructorStatementA) {
                size++;
            }
            if (comp.instructorStatementB) {
                size++;
            }
        });
        return size;
    }

    /**
     * calculate the total number of plagiarism cases
     */
    numberOfCases(): number {
        return this.confirmedComparisons.length * 2;
    }

    /**
     * calculate the total number of student statements
     */
    numberOfStudentStatements(): number {
        let size = 0;
        this.confirmedComparisons.forEach((comparison) => {
            if (comparison.studentStatementA) {
                size++;
            }
            if (comparison.studentStatementB) {
                size++;
            }
        });
        return size;
    }

    /**
     * calculate the number of plagiarism comparisons with a final status
     */
    numberOfFinalStatuses(): number {
        let size = 0;
        this.confirmedComparisons.forEach((comparison) => {
            if (comparison.statusA && comparison.statusA !== PlagiarismStatus.NONE) {
                size++;
            }
            if (comparison.statusB && comparison.statusB !== PlagiarismStatus.NONE) {
                size++;
            }
        });
        return size;
    }
}
