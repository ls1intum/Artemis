import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { ChannelsOverviewDialogComponent } from './dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelItemComponent } from './dialogs/channels-overview-dialog/channel-item/channel-item.component';
import { ChannelFormComponent } from './dialogs/channels-create-dialog/channel-form/channel-form.component';
import { ChannelsCreateDialogComponent } from './dialogs/channels-create-dialog/channels-create-dialog.component';
import { ConversationHeaderComponent } from './layout/conversation-header/conversation-header.component';
import { ChannelIconComponent } from './other/channel-icon/channel-icon.component';
import { ConversationAddUsersDialogComponent } from './dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { ConversationAddUsersFormComponent } from './dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
import { CourseUsersSelectorModule } from 'app/shared/course-users-selector/course-users-selector.module';
import { ConversationMembersComponent } from './dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-members.component';
import { ConversationDetailDialogComponent } from './dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { ConversationInfoComponent } from './dialogs/conversation-detail-dialog/tabs/conversation-info/conversation-info.component';
import { ConversationMemberRowComponent } from './dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-member-row/conversation-member-row.component';
import { GenericUpdateTextPropertyDialogComponent } from './dialogs/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { GenericConfirmationDialogComponent } from './dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ConversationSettingsComponent } from './dialogs/conversation-detail-dialog/tabs/conversation-settings/conversation-settings.component';
import { OneToOneChatCreateDialogComponent } from './dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { GroupChatCreateDialogComponent } from './dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { GroupChatIconComponent } from './other/group-chat-icon/group-chat-icon.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { CourseConversationsCodeOfConductComponent } from 'app/overview/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { CourseWideSearchComponent } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.conversationsLayout.tabTitle',
        },
        component: CourseConversationsComponent,
    },
];

@NgModule({
    imports: [
        RouterModule.forChild(routes),
        MetisModule,
        ArtemisMarkdownModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisDataTableModule,
        ArtemisSidebarModule,
        InfiniteScrollModule,
        CourseUsersSelectorModule,
        ProfilePictureComponent,
    ],
    declarations: [
        CourseConversationsComponent,
        CourseConversationsCodeOfConductComponent,
        ConversationThreadSidebarComponent,
        ConversationMessagesComponent,
        ChannelsOverviewDialogComponent,
        ChannelItemComponent,
        ChannelFormComponent,
        ChannelsCreateDialogComponent,
        ConversationHeaderComponent,
        ChannelIconComponent,
        ConversationAddUsersDialogComponent,
        ConversationAddUsersFormComponent,
        ConversationMembersComponent,
        ConversationDetailDialogComponent,
        ConversationInfoComponent,
        ConversationMemberRowComponent,
        GenericUpdateTextPropertyDialogComponent,
        GenericConfirmationDialogComponent,
        ConversationSettingsComponent,
        OneToOneChatCreateDialogComponent,
        GroupChatCreateDialogComponent,
        GroupChatIconComponent,
        CourseWideSearchComponent,
    ],
})
export class CourseConversationsModule {}
