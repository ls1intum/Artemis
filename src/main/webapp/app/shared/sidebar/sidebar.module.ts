import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { SidebarAccordionComponent } from './sidebar-accordion/sidebar-accordion.component';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from 'app/shared/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardItemComponent } from './sidebar-card-item/sidebar-card-item.component';
import { SidebarComponent } from './sidebar.component';
import { ArtemisSharedCommonModule } from '../shared-common.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { SidebarCardDirective } from 'app/shared/sidebar/sidebar-card.directive';
import { ConversationOptionsComponent } from 'app/shared/sidebar/conversation-options/conversation-options.component';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ArtemisSharedPipesModule,
        ArtemisSidePanelModule,
        ArtemisSharedCommonModule,
        SubmissionResultStatusModule,
        SidebarCardDirective,
        ArtemisExamSharedModule,
    ],
    declarations: [
        SidebarAccordionComponent,
        SidebarCardSmallComponent,
        SidebarCardMediumComponent,
        SidebarCardLargeComponent,
        SidebarCardItemComponent,
        SidebarComponent,
        ConversationOptionsComponent,
    ],
    exports: [SidebarComponent, SidebarCardSmallComponent, SidebarCardMediumComponent, SidebarCardLargeComponent, ConversationOptionsComponent],
})
export class ArtemisSidebarModule {}
