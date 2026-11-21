import {
    TumAetUiButtonDirective,
    TumAetUiPaginatorComponent,
    TumAetUiTableDirective,
    TumAetUiTableLayoutComponent,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RatingApi } from 'app/openapi/api/rating-api';
import { RatingListItem, RatingListItemAssessmentTypeEnum, RatingListItemExerciseTypeEnum } from 'app/openapi/model/rating-list-item';
import { toPageableResult } from 'app/foundation/pagination/pageable-table';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { TranslatePipe } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { map } from 'rxjs/operators';

const exerciseTypeByResponse: Record<RatingListItemExerciseTypeEnum, ExerciseType> = {
    text: ExerciseType.TEXT,
    programming: ExerciseType.PROGRAMMING,
    modeling: ExerciseType.MODELING,
    'file-upload': ExerciseType.FILE_UPLOAD,
    quiz: ExerciseType.QUIZ,
};

@Component({
    selector: 'jhi-rating-list',
    templateUrl: './rating-list.component.html',
    imports: [
        TranslatePipe,
        FaIconComponent,
        StarRatingComponent,
        TumAetUiPaginatorComponent,
        TumAetUiTooltipDirective,
        TumAetUiButtonDirective,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
    ],
})
export class RatingListComponent implements OnInit {
    private ratingApi = inject(RatingApi);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    readonly ratings = signal<RatingListItem[]>([]);
    readonly totalElements = signal(0);
    readonly page = signal(1); // 1-indexed (TUM AET UI paginator is 0-indexed; converted in onPageChange)
    public pageSize = 20;

    private courseId!: number; // set in ngOnInit() from route params

    private readonly sortingPredicateState = signal('id');
    private readonly reverseOrderState = signal(false);

    get ratingsSortingPredicate(): string {
        return this.sortingPredicateState();
    }
    set ratingsSortingPredicate(value: string) {
        this.sortingPredicateState.set(value);
    }

    get ratingsReverseOrder(): boolean {
        return this.reverseOrderState();
    }
    set ratingsReverseOrder(value: boolean) {
        this.reverseOrderState.set(value);
    }

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnInit(): void {
        this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.loadRatings();
        });
    }

    loadRatings(): void {
        // page is 1-indexed in the component, but Spring is 0-indexed
        const sortDirection = this.ratingsReverseOrder ? 'asc' : 'desc';
        const sort = `${this.ratingsSortingPredicate},${sortDirection}`;
        this.ratingApi
            .getRatingForInstructorDashboard(this.courseId, this.page() - 1, this.pageSize, [sort])
            .pipe(map(toPageableResult))
            .subscribe((pageResult) => {
                this.ratings.set(pageResult.content);
                this.totalElements.set(pageResult.totalElements);
            });
    }

    protected onSortChange(event: TumAetUiTableSortEvent): void {
        this.ratingsSortingPredicate = event.field;
        this.ratingsReverseOrder = event.order === 1;
        this.sortRows();
    }

    sortRows(): void {
        this.loadRatings();
    }

    onPageChange(page: number): void {
        this.page.set(page + 1);
        this.loadRatings();
    }

    openResult(rating: RatingListItem): void {
        // Navigate to the appropriate assessment view based on exercise type
        const exerciseTypePath = this.getExerciseTypePath(rating.exerciseType);

        if (rating.exerciseType === RatingListItemExerciseTypeEnum.Programming) {
            // Programming exercises use a different route structure (no resultId)
            void this.router.navigate(['/course-management', this.courseId, exerciseTypePath, rating.exerciseId, 'submissions', rating.submissionId, 'assessment']);
        } else {
            // Text, Modeling, and File Upload exercises use assessments/:resultId
            void this.router.navigate([
                '/course-management',
                this.courseId,
                exerciseTypePath,
                rating.exerciseId,
                'submissions',
                rating.submissionId,
                'assessments',
                rating.resultId,
            ]);
        }
    }

    /**
     * Get the route path segment for an exercise type.
     */
    private getExerciseTypePath(exerciseType?: RatingListItem['exerciseType']): string {
        switch (exerciseType) {
            case RatingListItemExerciseTypeEnum.Text:
                return 'text-exercises';
            case RatingListItemExerciseTypeEnum.Programming:
                return 'programming-exercises';
            case RatingListItemExerciseTypeEnum.Modeling:
                return 'modeling-exercises';
            case RatingListItemExerciseTypeEnum.FileUpload:
                return 'file-upload-exercises';
            default:
                return 'exercises';
        }
    }

    /**
     * Get the icon for an exercise type.
     */
    getExerciseIcon(exerciseType?: RatingListItem['exerciseType']): IconProp {
        return getIcon(exerciseType && exerciseTypeByResponse[exerciseType]);
    }

    /**
     * Get the translation key for an exercise type tooltip.
     */
    getExerciseTypeTooltip(exerciseType?: RatingListItem['exerciseType']): string {
        return getIconTooltip(exerciseType && exerciseTypeByResponse[exerciseType]);
    }

    /**
     * Get the translation key for an assessment type.
     */
    getAssessmentTypeTranslationKey(assessmentType?: RatingListItem['assessmentType']): string {
        if (!assessmentType) {
            return 'artemisApp.AssessmentType.null';
        }
        return `artemisApp.AssessmentType.${assessmentType}`;
    }

    protected readonly AssessmentType = RatingListItemAssessmentTypeEnum;
}
