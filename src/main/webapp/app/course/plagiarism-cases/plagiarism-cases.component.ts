import { Component, OnInit } from '@angular/core';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { PlagiarismCase } from 'app/course/plagiarism-cases/types/PlagiarismCase';
import { ActivatedRoute } from '@angular/router';
import { downloadFile } from 'app/shared/util/download.util';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';

@Component({
    selector: 'jhi-plagiarism-cases',
    templateUrl: './plagiarism-cases.component.html',
    styles: [],
})
export class PlagiarismCasesComponent implements OnInit {
    confirmedPlagiarismCases: PlagiarismCase[] | undefined;
    courseId: number;
    hideFinished = false;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.plagiarismCasesService
            .getPlagiarismCases(this.courseId)
            .toPromise()
            .then((cases) => {
                this.confirmedPlagiarismCases = cases!;
            });
    }

    export(): void {
        const blobParts: string[] = ['Student login,Exercise,Similarity,Status\n'];
        this.confirmedPlagiarismCases?.forEach((c) => {
            c.comparisons.forEach((comp) => {
                const exerciseTitleCSVSanitized = c.exercise.title?.replace(',', '","');
                if (comp.statusA === PlagiarismStatus.CONFIRMED) {
                    blobParts.push(`${comp.submissionA.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},${comp.statusA}\n`);
                } else if (!comp.statementA) {
                    blobParts.push(`${comp.submissionA.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},No statement from student\n`);
                }
                if (comp.statusB === PlagiarismStatus.CONFIRMED) {
                    blobParts.push(`${comp.submissionB.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},${comp.statusB}\n`);
                } else if (!comp.statementB) {
                    blobParts.push(`${comp.submissionB.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},No statement from student\n`);
                }
            });
        });
        downloadFile(new Blob(blobParts, { type: 'text/csv' }), 'plagiarism-cases.csv');
    }

    notificationsSent(): number {
        let size = 0;
        this.confirmedPlagiarismCases?.forEach((c) => {
            c.comparisons.forEach((comp) => {
                if (comp.notificationA) {
                    size++;
                }
                if (comp.notificationB) {
                    size++;
                }
            });
        });
        return size;
    }

    numberOfCases(): number {
        let size = 0;
        this.confirmedPlagiarismCases!.forEach((c) => {
            c.comparisons.forEach(() => (size += 2));
        });
        return size;
    }

    numberOfResponses(): number {
        let size = 0;
        this.confirmedPlagiarismCases!.forEach((c) => {
            c.comparisons.forEach((cmp) => {
                if (cmp.statementA) {
                    size++;
                }
                if (cmp.statementB) {
                    size++;
                }
            });
        });
        return size;
    }

    responsesAssessed(): number {
        let size = 0;
        this.confirmedPlagiarismCases?.forEach((c) => {
            c.comparisons.forEach((cmp) => {
                if (cmp.statusA) {
                    size++;
                }
                if (cmp.statusB) {
                    size++;
                }
            });
        });
        return size;
    }
}
