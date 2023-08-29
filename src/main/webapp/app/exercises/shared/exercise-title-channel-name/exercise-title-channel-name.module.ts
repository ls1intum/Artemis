import { NgModule } from '@angular/core';
import { ExerciseTitleChannelNameComponent } from './exercise-title-channel-name.component';
import { TitleChannelNameModule } from 'app/shared/form/title-channel-name/title-channel-name.module';

@NgModule({
    declarations: [ExerciseTitleChannelNameComponent],
    imports: [TitleChannelNameModule],
    exports: [ExerciseTitleChannelNameComponent],
})
export class ExerciseTitleChannelNameModule {}
