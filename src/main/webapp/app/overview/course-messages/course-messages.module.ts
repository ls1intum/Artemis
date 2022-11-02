import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ConversationSidebarComponent } from 'app/overview/course-messages/conversation-sidebar/conversation-sidebar.component';
import { CourseMessagesComponent } from 'app/overview/course-messages/course-messages.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { MessagesComponent } from 'app/overview/course-messages/messages/messages.component';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { ThreadSidebarComponent } from 'app/overview/course-messages/thread-sidebar/thread-sidebar.component';
import { SidebarSectionComponent } from './conversation-sidebar/sidebar-section/sidebar-section.component';
import { ChannelsOverviewDialogComponent } from './channels/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelItemComponent } from './channels/channels-overview-dialog/channel-item/channel-item.component';
import { ChannelFormComponent } from './channels/channel-form/channel-form.component';
import { ChannelsCreateDialogComponent } from './channels/channels-create-dialog/channels-create-dialog.component';
import { ConversationHeaderComponent } from './conversation-header/conversation-header.component';
import { ChannelIconComponent } from './channels/channel-icon/channel-icon.component';
import { ConversationAddUsersDialogComponent } from './conversation-add-users-dialog/conversation-add-users-dialog.component';
import { AddUsersFormComponent } from './conversation-add-users-dialog/add-users-form/add-users-form.component';
import { CourseUsersSelectorModule } from 'app/shared/course-users-selector/course-users-selector.module';
import { ConversationMembers } from './conversation-detail-dialog/conversation-members/conversation-members.component';
import { ConversationDetailDialogComponent } from './conversation-detail-dialog/conversation-detail-dialog.component';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.messages.label',
        },
        component: CourseMessagesComponent,
    },
];

@NgModule({
    imports: [
        RouterModule.forChild(routes),
        MetisModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisDataTableModule,
        InfiniteScrollModule,
        CourseUsersSelectorModule,
    ],
    declarations: [
        CourseMessagesComponent,
        ConversationSidebarComponent,
        ThreadSidebarComponent,
        MessagesComponent,
        SidebarSectionComponent,
        ChannelsOverviewDialogComponent,
        ChannelItemComponent,
        ChannelFormComponent,
        ChannelsCreateDialogComponent,
        ConversationHeaderComponent,
        ChannelIconComponent,
        ConversationAddUsersDialogComponent,
        AddUsersFormComponent,
        ConversationMembers,
        ConversationDetailDialogComponent,
    ],
})
export class CourseMessagesModule {}
