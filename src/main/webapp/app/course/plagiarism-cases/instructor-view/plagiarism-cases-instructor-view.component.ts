import { HttpResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Exercise, getIcon } from 'app/entities/exercise.model';
import { downloadFile } from 'app/shared/util/download.util';

@Component({
    selector: 'jhi-plagiarism-cases-instructor-view',
    templateUrl: './plagiarism-cases-instructor-view.component.html',
})
export class PlagiarismCasesInstructorViewComponent implements OnInit {
    courseId: number;
    plagiarismCases: PlagiarismCase[] = [];
    groupedPlagiarismCases: any;
    exercisesWithPlagiarismCases: Exercise[] = [];
    getIcon = getIcon;

    constructor(private plagiarismCasesService: PlagiarismCasesService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.plagiarismCasesService.getPlagiarismCasesForInstructor(this.courseId).subscribe({
            next: (res: HttpResponse<PlagiarismCase[]>) => {
                this.plagiarismCases = res.body!;
                this.groupedPlagiarismCases = this.plagiarismCases.reduce((acc, plagiarismCase) => {
                    // Group initialization
                    if (!acc[plagiarismCase.exercise!.id!]) {
                        acc[plagiarismCase.exercise!.id!] = [];
                        this.exercisesWithPlagiarismCases.push(plagiarismCase.exercise!);
                    }

                    // Grouping
                    acc[plagiarismCase.exercise!.id!].push(plagiarismCase);

                    return acc;
                }, {});
            },
        });
    }

    /**
     * calculate the total number of plagiarism cases
     * @return number of plagiarism cases in course
     */
    numberOfCases(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.length;
    }

    /**
     * calculate the number of plagiarism cases with a verdict
     * @return number of plagiarism cases with a verdict in course
     */
    numberOfCasesWithVerdict(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.filter((plagiarismCase) => !!plagiarismCase.verdict).length;
    }

    numberOfCasesWithPost(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.filter((plagiarismCase) => !!plagiarismCase.post).length;
    }

    /**
     * export the plagiarism cases in CSV format
     */
    exportPlagiarismCases(): void {
        const blobParts: string[] = ['Student Login,Exercise,Verdict, Verdict Date\n'];
        this.plagiarismCases.forEach((plagiarismCase) => {
            const exerciseTitleCSVSanitized = plagiarismCase.exercise?.title?.replace(',', '","');
            if (plagiarismCase.verdict) {
                blobParts.push(
                    `${plagiarismCase.student?.login},${exerciseTitleCSVSanitized},${plagiarismCase.verdict},${plagiarismCase.verdictDate},${plagiarismCase.verdictBy!.name}\n`,
                );
            } else {
                blobParts.push(`${plagiarismCase.student?.login},${exerciseTitleCSVSanitized},No verdict yet, -, -\n`);
            }
        });
        downloadFile(new Blob(blobParts, { type: 'text/csv' }), 'plagiarism-cases.csv');
    }
}
