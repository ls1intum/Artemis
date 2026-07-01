import { Component, OnInit, inject, signal } from '@angular/core';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { RatingListItem } from 'app/assessment/shared/entities/rating-list-item.model';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslatePipe } from '@ngx-translate/core';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-rating-list',
    templateUrl: './rating-list.component.html',
    imports: [TranslatePipe, SortDirective, SortByDirective, FaIconComponent, StarRatingComponent, PaginatorModule, NgbTooltip],
})
export class RatingListComponent implements OnInit {
    private ratingService = inject(RatingService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    readonly ratings = signal<RatingListItem[]>([]);
    readonly totalElements = signal(0);
    readonly page = signal(1); // 1-indexed (PrimeNG paginator is 0-indexed; converted in onPageChange)
    public pageSize = 20;

    private courseId: number;

    ratingsSortingPredicate = 'id';
    ratingsReverseOrder = false;

    // Icons
    faSort = faSort;
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
        this.ratingService.getRatingsForDashboard(this.courseId, this.page() - 1, this.pageSize, sort).subscribe((pageResult) => {
            this.ratings.set(pageResult.content);
            this.totalElements.set(pageResult.totalElements);
        });
    }

    sortRows(): void {
        this.loadRatings();
    }

    onPageChange(event: PaginatorState): void {
        this.page.set((event.page ?? 0) + 1);
        this.loadRatings();
    }

    openResult(rating: RatingListItem): void {
        // Navigate to the appropriate assessment view based on exercise type
        const exerciseTypePath = this.getExerciseTypePath(rating.exerciseType);

        if (rating.exerciseType === ExerciseType.PROGRAMMING) {
            // Programming exercises use a different route structure (no resultId)
            this.router.navigate(['/course-management', this.courseId, exerciseTypePath, rating.exerciseId, 'submissions', rating.submissionId, 'assessment']);
        } else {
            // Text, Modeling, and File Upload exercises use assessments/:resultId
            this.router.navigate(['/course-management', this.courseId, exerciseTypePath, rating.exerciseId, 'submissions', rating.submissionId, 'assessments', rating.resultId]);
        }
    }

    /**
     * Get the route path segment for an exercise type.
     */
    private getExerciseTypePath(exerciseType?: ExerciseType): string {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return 'text-exercises';
            case ExerciseType.PROGRAMMING:
                return 'programming-exercises';
            case ExerciseType.MODELING:
                return 'modeling-exercises';
            case ExerciseType.FILE_UPLOAD:
                return 'file-upload-exercises';
            default:
                return 'exercises';
        }
    }

    /**
     * Get the icon for an exercise type.
     */
    getExerciseIcon(exerciseType?: ExerciseType): IconProp {
        return getIcon(exerciseType);
    }

    /**
     * Get the translation key for an exercise type tooltip.
     */
    getExerciseTypeTooltip(exerciseType?: ExerciseType): string {
        return getIconTooltip(exerciseType);
    }

    /**
     * Get the translation key for an assessment type.
     */
    getAssessmentTypeTranslationKey(assessmentType?: AssessmentType): string {
        if (!assessmentType) {
            return 'artemisApp.AssessmentType.null';
        }
        return `artemisApp.AssessmentType.${assessmentType}`;
    }

    protected readonly AssessmentType = AssessmentType;
}
