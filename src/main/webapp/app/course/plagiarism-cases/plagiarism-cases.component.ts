import { Component, OnInit } from '@angular/core';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { ActivatedRoute } from '@angular/router';
import { downloadFile } from 'app/shared/util/download.util';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { HttpResponse } from '@angular/common/http';
import { getIcon } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-plagiarism-cases',
    templateUrl: './plagiarism-cases.component.html',
    styles: [],
})
export class PlagiarismCasesComponent implements OnInit {
    getIcon = getIcon;
    confirmedPlagiarismCases: PlagiarismCase[] | undefined;
    courseId: number;
    hideFinished = false;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.plagiarismCasesService.getPlagiarismCases(this.courseId).subscribe((resp: HttpResponse<PlagiarismCase[]>) => {
            this.confirmedPlagiarismCases = resp.body!;
        });
    }

    /**
     * export the plagiarism cases in CSV format
     */
    export(): void {
        const blobParts: string[] = ['Student login,Exercise,Similarity,Status\n'];
        this.confirmedPlagiarismCases?.forEach((c) => {
            c.comparisons.forEach((comp) => {
                const exerciseTitleCSVSanitized = c.exercise.title?.replace(',', '","');
                if (comp.statusA === PlagiarismStatus.CONFIRMED) {
                    blobParts.push(`${comp.submissionA.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},${comp.statusA}\n`);
                } else if (!comp.studentStatementA) {
                    blobParts.push(`${comp.submissionA.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},No statement from student\n`);
                }
                if (comp.statusB === PlagiarismStatus.CONFIRMED) {
                    blobParts.push(`${comp.submissionB.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},${comp.statusB}\n`);
                } else if (!comp.studentStatementB) {
                    blobParts.push(`${comp.submissionB.studentLogin},${exerciseTitleCSVSanitized},${comp.similarity},No statement from student\n`);
                }
            });
        });
        downloadFile(new Blob(blobParts, { type: 'text/csv' }), 'plagiarism-cases.csv');
    }

    /**
     * calculate the total number of instructor statements
     */
    numberOfInstructorStatements(): number {
        let size = 0;
        this.confirmedPlagiarismCases?.forEach((c) => {
            c.comparisons.forEach((comp) => {
                if (comp.instructorStatementA) {
                    size++;
                }
                if (comp.instructorStatementB) {
                    size++;
                }
            });
        });
        return size;
    }

    /**
     * calculate the total number of plagiarism cases
     */
    numberOfCases(): number {
        let size = 0;
        this.confirmedPlagiarismCases!.forEach((c) => {
            c.comparisons.forEach(() => (size += 2));
        });
        return size;
    }

    /**
     * calculate the total number of student statements
     */
    numberOfStudentStatements(): number {
        let size = 0;
        this.confirmedPlagiarismCases!.forEach((c) => {
            c.comparisons.forEach((cmp) => {
                if (cmp.studentStatementA) {
                    size++;
                }
                if (cmp.studentStatementB) {
                    size++;
                }
            });
        });
        return size;
    }

    /**
     * calculate the number of plagiarism comparisons with a final status
     */
    numberOfFinalStatuses(): number {
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
