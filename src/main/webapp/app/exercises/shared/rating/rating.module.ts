import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { RatingListComponent } from './rating-list/rating-list.component';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';

@NgModule({
    declarations: [RatingComponent, RatingListComponent, StarRatingComponent],
    exports: [RatingComponent, StarRatingComponent],
    imports: [CommonModule, ArtemisSharedModule],
})
export class RatingModule {}
