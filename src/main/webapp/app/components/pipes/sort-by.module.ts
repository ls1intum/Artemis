import { NgModule } from '@angular/core';
import { SortByPipe } from './sort-by.pipe';

@NgModule({
    declarations: [
        SortByPipe
    ],
    exports: [
        SortByPipe
    ]
})
export class SortByModule {}
