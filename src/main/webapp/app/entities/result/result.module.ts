import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ResultComponent, ResultDetailComponent, ResultService, UpdatingResultComponent } from './';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';
import { ArtemisProgrammingSubmissionModule } from 'app/programming-submission/programming-submission.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisProgrammingSubmissionModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent],
    providers: [ResultService],
})
export class ArtemisResultModule {}
