import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
})
export class ArtemisProgrammingSubmissionModule {
    static forRoot() {
        return {
            ngModule: ArtemisProgrammingSubmissionModule,
        };
    }
}
