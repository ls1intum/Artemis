import { MetisModule } from 'app/shared/metis/metis.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ConversationSidebarComponent } from 'app/overview/course-messages/conversation-sidebar/conversation-sidebar.component';
import { CourseMessagesComponent } from 'app/overview/course-messages/course-messages.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';

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
    imports: [RouterModule.forChild(routes), MetisModule, ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisDataTableModule],
    declarations: [CourseMessagesComponent, ConversationSidebarComponent],
    exports: [ConversationSidebarComponent],
})
export class CourseMessagesModule {}
