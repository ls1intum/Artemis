import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../../shared';
import { ResultComponent, ResultDetailComponent, ResultService } from './';
import { MomentModule } from 'angular2-moment';
import { ResultHistoryComponent } from "app/entities/result/result-history.component";

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        MomentModule
    ],
    declarations: [
        ResultComponent,
        ResultDetailComponent,
        ResultHistoryComponent
    ],
    exports: [
        ResultComponent,
        ResultDetailComponent,
        ResultHistoryComponent
    ],
    entryComponents: [
        ResultComponent,
        ResultDetailComponent
    ],
    providers: [
        ResultService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSResultModule {}
