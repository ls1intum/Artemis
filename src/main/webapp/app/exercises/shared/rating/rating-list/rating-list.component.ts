import { Component, OnInit, inject } from '@angular/core';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { Rating } from 'app/entities/rating.model';
import { ActivatedRoute, Router } from '@angular/router';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-rating-list',
    templateUrl: './rating-list.component.html',
    styleUrls: [],
})
export class RatingListComponent implements OnInit {
    private ratingService = inject(RatingService);
    private route = inject(ActivatedRoute);
    private sortService = inject(SortService);
    private router = inject(Router);

    public ratings: Rating[] = [];

    private courseId: number;

    ratingsSortingPredicate = 'id';
    ratingsReverseOrder = false;

    // Icons
    faSort = faSort;
    faFolderOpen = faFolderOpen;

    ngOnInit(): void {
        this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });

        this.ratingService.getRatingsForDashboard(this.courseId).subscribe((ratings) => {
            this.ratings = ratings;
        });
    }

    sortRows() {
        this.sortService.sortByProperty(this.ratings, this.ratingsSortingPredicate, this.ratingsReverseOrder);
    }

    openResult(rating: Rating) {
        const participation = rating.result?.participation;
        const exercise = rating.result?.participation?.exercise;

        if (participation && exercise) {
            if (exercise.type === ExerciseType.PROGRAMMING) {
                this.router.navigate(['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'code-editor', participation.id]);
            } else {
                this.router.navigate(['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'participate', participation.id]);
            }
        }
    }
}
