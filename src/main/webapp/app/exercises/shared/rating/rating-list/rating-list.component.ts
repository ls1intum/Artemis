import { Component, OnInit } from '@angular/core';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { Rating } from 'app/entities/rating.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-rating-list',
    templateUrl: './rating-list.component.html',
    styleUrls: [],
})
export class RatingListComponent implements OnInit {
    public ratings: Rating[] = [];

    private courseId: number;

    ratingsSortingPredicate = 'id';
    ratingsReverseOrder = false;

    constructor(private ratingService: RatingService, private route: ActivatedRoute, private location: Location, private sortService: SortService, private router: Router) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
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
            this.router.navigate(['/courses', this.courseId, `${exercise.type}-exercises`, exercise.id, 'participate', participation.id]);
        }
    }
}
