import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ChatSessionSidebarComponent } from 'app/overview/course-messages/chat-sessions-sidebar/chat-session-sidebar.component';
import { MessagesComponent } from 'app/overview/course-messages/messages/messages.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            pageTitle: 'artemisApp.messages.label',
        },
        component: MessagesComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisDataTableModule],
    declarations: [MessagesComponent, ChatSessionSidebarComponent],
    exports: [ChatSessionSidebarComponent],
})
export class CourseMessagesModule {}
