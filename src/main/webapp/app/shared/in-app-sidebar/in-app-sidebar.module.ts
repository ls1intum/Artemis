import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { InAppSidebarAccordionComponent } from './in-app-sidebar-accordion/in-app-sidebar-accordion.component';
import { InAppSidebarCardComponent } from './in-app-sidebar-card/in-app-sidebar-card.component';
import { InAppSidebarCardItemComponent } from './in-app-sidebar-card-item/in-app-sidebar-card-item.component';
import { InAppSidebarComponent } from './in-app-sidebar.component';
import { ArtemisSharedCommonModule } from '../shared-common.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ArtemisSharedPipesModule,
        ArtemisSidePanelModule,
        ArtemisCoursesRoutingModule,
        ArtemisSharedCommonModule,
        SubmissionResultStatusModule,
    ],
    declarations: [InAppSidebarAccordionComponent, InAppSidebarCardComponent, InAppSidebarCardItemComponent, InAppSidebarComponent],
    exports: [InAppSidebarComponent, InAppSidebarCardComponent],
})
export class ArtemisInAppSidebarModule {}
