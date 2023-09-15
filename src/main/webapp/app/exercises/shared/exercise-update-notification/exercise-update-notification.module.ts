import { NgModule } from '@angular/core';
import { ExerciseUpdateNotificationComponent } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    declarations: [ExerciseUpdateNotificationComponent],
    imports: [ArtemisSharedCommonModule],
    exports: [ExerciseUpdateNotificationComponent],
})
export class ExerciseUpdateNotificationModule {}
