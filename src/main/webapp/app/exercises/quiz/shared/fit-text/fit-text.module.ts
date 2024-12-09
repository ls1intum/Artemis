import { NgModule } from '@angular/core';
import { FitTextDirective } from 'app/exercises/quiz/shared/fit-text/fit-text.directive';

@NgModule({
    imports: [FitTextDirective],
    exports: [FitTextDirective],
})
export class FitTextModule {}
