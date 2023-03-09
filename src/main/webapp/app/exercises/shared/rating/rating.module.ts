import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RatingListComponent } from './rating-list/rating-list.component';

@NgModule({
    declarations: [RatingComponent, RatingListComponent, StarRatingComponent],
    exports: [RatingComponent, StarRatingComponent],
    imports: [CommonModule, ArtemisSharedModule],
})
export class RatingModule {}
