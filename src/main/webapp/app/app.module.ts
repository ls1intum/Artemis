import './vendor.ts';

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ArtemisSystemNotificationModule } from 'app/core/system-notification/system-notification.module';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { ArtemisNotificationModule } from 'app/overview/notification/notification.module';
import { NotificationSidebarComponent } from 'app/shared/layouts/notification-sidebar/notification-sidebar.component';
import { PageRibbonComponent } from 'app/shared/layouts/profiles/page-ribbon.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SystemNotificationComponent } from 'app/shared/layouts/system-notification/system-notification.component';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisConnectionNotificationModule } from 'app/shared/layouts/connection-notification/connection-notification.module';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { ArtemisLegalModule } from 'app/core/legal/legal.module';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { ErrorComponent } from 'app/shared/layouts/error/error.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHomeModule } from 'app/home/home.module';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';

// NOTE: this module should only include the most important modules for normal users, all course management, admin and account functionality should be lazy loaded if possible
@NgModule({
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        ArtemisSharedModule,
        ArtemisCoreModule,
        ArtemisHomeModule,
        ArtemisAppRoutingModule,
        ArtemisConnectionNotificationModule,
        GuidedTourModule,
        ArtemisLegalModule,
        ArtemisTeamModule,
        ArtemisCoursesModule,
        ArtemisSystemNotificationModule,
        ArtemisComplaintsModule,
        ArtemisNotificationModule,
        ArtemisHeaderExercisePageWithDetailsModule,
    ],
    declarations: [
        JhiMainComponent,
        NavbarComponent,
        ErrorComponent,
        OrionOutdatedComponent,
        PageRibbonComponent,
        ActiveMenuDirective,
        FooterComponent,
        NotificationSidebarComponent,
        SystemNotificationComponent,
    ],
    bootstrap: [JhiMainComponent],
})
export class ArtemisAppModule {}
