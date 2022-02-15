import { NgModule } from '@angular/core';
import { FitTextDirective } from 'app/exercises/quiz/shared/fit-text/fit-text.directive';

@NgModule({
    declarations: [FitTextDirective],
    exports: [FitTextDirective],
})
export class FitTextModule {}
