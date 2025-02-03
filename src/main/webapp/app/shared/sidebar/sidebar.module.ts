import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
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
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedPipesModule,
        ArtemisSharedCommonModule,
        SubmissionResultStatusModule,
        SidebarCardDirective,
        SearchFilterComponent,
        ProfilePictureComponent,
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
