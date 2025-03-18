import { HttpResponse } from '@angular/common/http';
import { Component, ElementRef, OnInit, effect, inject, viewChildren } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PlagiarismCasesService } from 'app/plagiarism/shared/plagiarism-cases.service';
import { PlagiarismCase } from 'app/plagiarism/shared/types/PlagiarismCase';
import { Exercise, getExerciseUrlSegment, getIcon } from 'app/entities/exercise.model';
import { downloadFile } from 'app/shared/util/download.util';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { GroupedPlagiarismCases } from 'app/plagiarism/shared/types/GroupedPlagiarismCase';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { PlagiarismCaseVerdictComponent } from 'app/plagiarism/shared/verdict/plagiarism-case-verdict.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-plagiarism-cases-instructor-view',
    templateUrl: './plagiarism-cases-instructor-view.component.html',
    styleUrls: ['./plagiarism-cases-instructor-view.component.scss'],
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        FaIconComponent,
        RouterLink,
        ProgressBarComponent,
        PlagiarismCaseVerdictComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class PlagiarismCasesInstructorViewComponent implements OnInit {
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);

    courseId: number;
    examId?: number;
    plagiarismCases: PlagiarismCase[] = [];
    groupedPlagiarismCases: GroupedPlagiarismCases;
    exercisesWithPlagiarismCases: Exercise[] = [];

    exerciseWithPlagCasesElements = viewChildren<ElementRef>('plagExerciseElement');

    // method called as html template variable, angular only recognises reference variables in html if they are a property
    // of the corresponding component class
    getExerciseUrlSegment = getExerciseUrlSegment;

    readonly getIcon = getIcon;
    readonly documentationType: DocumentationType = 'PlagiarismChecks';

    constructor() {
        // effect needs to be in constructor context, due to the possibility of ngOnInit being called from a non-injection
        //context
        effect(() => {
            const exerciseId = Number(this.route.snapshot.queryParamMap?.get('exerciseId'));
            if (exerciseId) {
                this.scrollToExerciseAfterViewInit(exerciseId);
            }
        });
    }

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        const plagiarismCasesForInstructor$ = this.examId
            ? this.plagiarismCasesService.getExamPlagiarismCasesForInstructor(this.courseId, this.examId)
            : this.plagiarismCasesService.getCoursePlagiarismCasesForInstructor(this.courseId);

        plagiarismCasesForInstructor$.subscribe({
            next: (res: HttpResponse<PlagiarismCase[]>) => {
                this.plagiarismCases = res.body!;
                this.groupedPlagiarismCases = this.getGroupedPlagiarismCasesByExercise(this.plagiarismCases);
            },
        });
    }

    /**
     * scroll to the exercise with
     */
    scrollToExerciseAfterViewInit(exerciseId: number) {
        const element = this.exerciseWithPlagCasesElements().find((elem) => elem.nativeElement.id === 'exercise-with-plagiarism-case-' + exerciseId);
        if (element) {
            element.nativeElement.scrollIntoView({
                behavior: 'smooth',
                block: 'start',
                inline: 'nearest',
            });
        }
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
        // sanitize the operators away in case they appear in the values
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

    /**
     * groups plagiarism cases by exercise for view
     * @param cases to be grouped by exerises
     * @private return object containing grouped cases
     */
    private getGroupedPlagiarismCasesByExercise(cases: PlagiarismCase[]): GroupedPlagiarismCases {
        return cases.reduce((acc: { [exerciseId: number]: PlagiarismCase[] }, plagiarismCase: PlagiarismCase) => {
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
        }, {});
    }
}
