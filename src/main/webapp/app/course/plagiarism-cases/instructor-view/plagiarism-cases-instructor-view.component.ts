import { HttpResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Exercise, getIcon } from 'app/entities/exercise.model';
import { downloadFile } from 'app/shared/util/download.util';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-plagiarism-cases-instructor-view',
    templateUrl: './plagiarism-cases-instructor-view.component.html',
})
export class PlagiarismCasesInstructorViewComponent implements OnInit {
    courseId: number;
    examId?: number;
    plagiarismCases: PlagiarismCase[] = [];
    groupedPlagiarismCases: any; // maybe? { [key: number]: PlagiarismCase[] }
    exercisesWithPlagiarismCases: Exercise[] = [];

    readonly getIcon = getIcon;
    readonly documentationType: DocumentationType = 'PlagiarismChecks';

    constructor(
        private plagiarismCasesService: PlagiarismCasesService,
        private route: ActivatedRoute,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));

        const plagiarismCasesForInstructor$ = this.examId
            ? this.plagiarismCasesService.getExamPlagiarismCasesForInstructor(this.courseId, this.examId)
            : this.plagiarismCasesService.getCoursePlagiarismCasesForInstructor(this.courseId);

        plagiarismCasesForInstructor$.subscribe({
            next: (res: HttpResponse<PlagiarismCase[]>) => {
                this.plagiarismCases = res.body!;
                this.groupedPlagiarismCases = this.plagiarismCases.reduce(
                    (
                        acc: {
                            [exerciseId: number]: PlagiarismCase[];
                        },
                        plagiarismCase,
                    ) => {
                        const caseExerciseId = plagiarismCase.exercise?.id;
                        if (caseExerciseId === undefined) {
                            return acc;
                        }

                        // Group initialization
                        if (!acc[caseExerciseId]) {
                            acc[caseExerciseId] = [];
                            this.exercisesWithPlagiarismCases.push(plagiarismCase.exercise!);
                        }

                        // Grouping
                        acc[caseExerciseId].push(plagiarismCase);

                        return acc;
                    },
                    {},
                );
            },
        });
    }

    /**
     * calculate the total number of plagiarism cases
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return number of plagiarism cases in course or exam
     */
    numberOfCases(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.length;
    }

    /**
     * calculate the number of plagiarism cases with a verdict
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return number of plagiarism cases with a verdict in course or exam
     */
    numberOfCasesWithVerdict(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.filter((plagiarismCase) => !!plagiarismCase.verdict).length;
    }

    /**
     * calculate the percentage of plagiarism cases with a verdict
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return percentage of plagiarism cases with a verdict in course or exam
     */
    percentageOfCasesWithVerdict(plagiarismCases: PlagiarismCase[]): number {
        return (this.numberOfCasesWithVerdict(plagiarismCases) / this.numberOfCases(plagiarismCases)) * 100 || 0;
    }

    /**
     * calculate the number of plagiarism cases with a post
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return number of plagiarism cases with a post in course or exam
     */
    numberOfCasesWithPost(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.filter((plagiarismCase) => !!plagiarismCase.post).length;
    }

    /**
     * calculate the percentage of plagiarism cases with a post
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return percentage of plagiarism cases with a post in course or exam
     */
    percentageOfCasesWithPost(plagiarismCases: PlagiarismCase[]): number {
        return (this.numberOfCasesWithPost(plagiarismCases) / this.numberOfCases(plagiarismCases)) * 100 || 0;
    }

    /**
     * calculate the number of plagiarism cases with an answer by the student in course or exam
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return number of plagiarism cases with an answer by the student in course or exam
     */
    numberOfCasesWithStudentAnswer(plagiarismCases: PlagiarismCase[]): number {
        return plagiarismCases.filter((plagiarismCase) => this.hasStudentAnswer(plagiarismCase)).length;
    }

    /**
     * calculate the percentage of plagiarism cases with an answer by the student in course or exam
     * @param plagiarismCases plagiarismCases in the course or exam
     * @return percentage of plagiarism cases with an answer by the student in course or exam
     */
    percentageOfCasesWithStudentAnswer(plagiarismCases: PlagiarismCase[]): number {
        return (this.numberOfCasesWithStudentAnswer(plagiarismCases) / this.numberOfCasesWithPost(plagiarismCases)) * 100 || 0;
    }

    /**
     * check if the student of a plagiarism case was already notified and has responded
     * @param plagiarismCase plagiarismCase to check
     * @return whether the student has responded or not
     */
    hasStudentAnswer(plagiarismCase: PlagiarismCase): boolean {
        return !!plagiarismCase.post && !!plagiarismCase.post.answers && plagiarismCase.post.answers.some((answer) => answer.author?.id === plagiarismCase.student?.id);
    }

    /**
     * set placeholder for undefined values and sanitize the operators away
     * @param value to be sanitized or replaced with -
     * @private
     */
    private sanitizeCSVField(value: any): string {
        if (value === null || value === undefined) {
            // used as placeholder for null or if the passed value does not exist
            return '-';
        }
        return String(value).replace(/;/g, '";"');
    }

    /**
     * export the cases in CSV format
     */
    exportPlagiarismCases(): void {
        const headers = ['Student Login', 'Matr. Nr.', 'Exercise', 'Verdict', 'Verdict Date', 'Verdict By'];
        const blobParts: string[] = [headers.join(';') + '\n'];
        this.plagiarismCases.reduce((acc, plagiarismCase) => {
            const fields = [
                this.sanitizeCSVField(plagiarismCase.student?.login),
                this.sanitizeCSVField(plagiarismCase.student?.visibleRegistrationNumber),
                this.sanitizeCSVField(plagiarismCase.exercise?.title),
            ];
            if (plagiarismCase.verdict) {
                fields.push(
                    this.sanitizeCSVField(plagiarismCase.verdict),
                    this.sanitizeCSVField(plagiarismCase.verdictDate),
                    this.sanitizeCSVField(plagiarismCase.verdictBy?.name),
                );
            } else {
                fields.push('No verdict yet', '-', '-');
            }
            acc.push(fields.join(';') + '\n');
            return acc;
        }, blobParts);

        try {
            downloadFile(new Blob(blobParts, { type: 'text/csv' }), 'plagiarism-cases.csv');
        } catch (error) {
            this.alertService.error('artemisApp.plagiarism.plagiarismCases.export.error');
        }
    }
}
