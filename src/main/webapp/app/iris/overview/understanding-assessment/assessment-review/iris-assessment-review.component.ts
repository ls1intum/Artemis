import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Course } from 'app/course/shared/entities/course.model';
import { FormsModule } from '@angular/forms';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription, finalize, forkJoin, map, switchMap, take } from 'rxjs';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { faCheck, faX } from '@fortawesome/free-solid-svg-icons';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { IrisAssessmentReviewService } from 'app/iris/overview/services/iris-assessment-review.service';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';

@Component({
    selector: 'jhi-iris-assessment-review',
    templateUrl: './iris-assessment-review.component.html',
    styleUrl: './iris-assessment-review.component.scss',
    imports: [FormsModule, NgxDatatableModule, CommonModule, DataTableComponent, TranslateDirective, ArtemisTranslatePipe, ExerciseActionButtonComponent],
})
export class IrisAssessmentReviewComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private exerciseService = inject(ExerciseService);
    private irisAssessmentReviewService = inject(IrisAssessmentReviewService);

    protected readonly faCheck = faCheck;
    protected readonly faX = faX;

    isLoading: boolean;

    course: Course;
    exercise: ProgrammingExercise;
    assessment: IrisAssessment;

    rows: QAExchangeDTO[];

    paramSub: Subscription;

    ngOnInit() {
        this.isLoading = true;
        this.paramSub = this.route.params
            .pipe(
                take(1),
                switchMap((params) =>
                    forkJoin({
                        courseRes: this.courseService.find(params['courseId']),
                        exerciseRes: this.exerciseService.find(params['exerciseId']),
                        assessmentRes: this.irisAssessmentReviewService.findWithPoints(params['assessmentId']),
                        rowsRes: this.irisAssessmentReviewService.getAssessmentChat(params['assessmentId']),
                    }),
                ),
                map(({ courseRes, exerciseRes, assessmentRes, rowsRes }) => ({
                    course: courseRes.body!,
                    exercise: exerciseRes.body!,
                    assessment: assessmentRes.body!,
                    rows: rowsRes.body!,
                })),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(({ course, exercise, assessment, rows }) => {
                this.course = course;
                this.exercise = exercise;
                this.assessment = assessment;
                this.rows = rows;
            });
    }

    acceptAnswers() {
        if (this.assessment.verdictReview === IrisVerdictReview.REJECTED || this.assessment.verdict === IrisVerdict.SUSPICIOUS) {
            this.swapVerifiedPoints();
        }
        this.assessment.verdictReview = IrisVerdictReview.ACCEPTED;

        this.irisAssessmentReviewService.acceptAnswers(this.assessment.id!).subscribe();
    }

    rejectAnswers() {
        if (this.assessment.verdictReview === IrisVerdictReview.ACCEPTED || this.assessment.verdict === IrisVerdict.UNSUSPICIOUS) {
            this.swapVerifiedPoints();
        }
        this.assessment.verdictReview = IrisVerdictReview.REJECTED;

        this.irisAssessmentReviewService.rejectAnswers(this.assessment.id!).subscribe();
    }

    swapVerifiedPoints() {
        const newVerifiedPoints = this.assessment.verifiedPointsOld;
        this.assessment.verifiedPointsOld = this.assessment.verifiedPoints;
        this.assessment.verifiedPoints = newVerifiedPoints;
    }

    translateIrisVerdict(verdict: IrisVerdict) {
        switch (verdict) {
            case IrisVerdict.SUSPICIOUS:
                return 'suspicious';
            case IrisVerdict.UNSUSPICIOUS:
                return 'unsuspicious';
        }
    }

    translateReview(review: IrisVerdictReview) {
        switch (review) {
            case IrisVerdictReview.ACCEPTED:
                return 'accepted';
            case IrisVerdictReview.REJECTED:
                return 'rejected';
        }
    }

    getPointChangeString(buttonEffect: IrisVerdictReview): string {
        if (this.assessment.verdictReview === IrisVerdictReview.ACCEPTED || this.assessment.verdictReview === IrisVerdictReview.REJECTED) {
            return this.assessment.verdictReview === buttonEffect ? '' : ' ' + (this.assessment.verifiedPoints ?? 0) + ' → ' + (this.assessment.verifiedPointsOld ?? 0);
        } else if (
            (this.assessment.verdict === IrisVerdict.SUSPICIOUS && buttonEffect === IrisVerdictReview.ACCEPTED) ||
            (this.assessment.verdict === IrisVerdict.UNSUSPICIOUS && buttonEffect === IrisVerdictReview.REJECTED)
        ) {
            return ' ' + (this.assessment.verifiedPoints ?? 0) + ' → ' + (this.assessment.verifiedPointsOld ?? 0);
        } else {
            return ' ' + (this.assessment.verifiedPoints ?? 0);
        }
    }

    protected readonly IrisVerdictReview = IrisVerdictReview;
}
