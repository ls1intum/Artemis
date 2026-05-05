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
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { faCheck, faX } from '@fortawesome/free-solid-svg-icons';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { IrisAssessmentReviewService } from 'app/iris/overview/services/iris-assessment-review.service';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';

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
    private participationService = inject(ParticipationService);
    private irisAssessmentReviewService = inject(IrisAssessmentReviewService);

    protected readonly faCheck = faCheck;
    protected readonly faX = faX;

    isLoading: boolean;

    course: Course;
    exercise: ProgrammingExercise;
    participation: ProgrammingExerciseStudentParticipation;
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
                        participationRes: this.participationService.find(params['participationId']),
                        rowsRes: this.irisAssessmentReviewService.getAssessmentChat(params['participationId']),
                    }),
                ),
                map(({ courseRes, exerciseRes, participationRes, rowsRes }) => ({
                    course: courseRes.body!,
                    exercise: exerciseRes.body!,
                    participation: participationRes.body!,
                    rows: rowsRes.body!,
                })),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(({ course, exercise, participation, rows }) => {
                this.course = course;
                this.exercise = exercise;
                this.participation = participation;
                this.rows = rows;
            });
    }

    acceptAnswers() {
        this.participation.irisVerdictReview = IrisVerdictReview.ACCEPTED;
        this.irisAssessmentReviewService.acceptAnswers(this.participation.id!).subscribe();
    }

    rejectAnswers() {
        this.participation.irisVerdictReview = IrisVerdictReview.REJECTED;
        this.irisAssessmentReviewService.rejectAnswers(this.participation.id!).subscribe();
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

    protected readonly IrisVerdictReview = IrisVerdictReview;
}
