import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { RatingModule as StarratingModule } from 'ng-starrating';
import { RatingListComponent } from './rating-list/rating-list.component';

@NgModule({
    declarations: [RatingComponent, RatingListComponent],
    exports: [RatingComponent, RatingListComponent],
    imports: [CommonModule, ArtemisSharedModule, StarratingModule],
})
export class RatingModule {}
