import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ServiceWorkerModule } from '@angular/service-worker';

import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ArtemisLegalModule } from 'app/core/legal/legal.module';
import { ThemeModule } from 'app/core/theme/theme.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisHomeModule } from 'app/home/home.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ErrorComponent } from 'app/shared/layouts/error/error.component';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { PageRibbonComponent } from 'app/shared/layouts/profiles/page-ribbon.component';
import { LoadingNotificationComponent } from 'app/shared/notification/loading-notification/loading-notification.component';
import { NotificationPopupComponent } from 'app/shared/notification/notification-popup/notification-popup.component';
import { NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { SystemNotificationComponent } from 'app/shared/notification/system-notification/system-notification.component';
import { ArtemisSystemNotificationModule } from 'app/shared/notification/system-notification/system-notification.module';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { UserSettingsModule } from 'app/shared/user-settings/user-settings.module';

// NOTE: this module should only include the most important modules for normal users, all course management, admin and account functionality should be lazy loaded if possible
@NgModule({
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        // This enables service worker (PWA)
        ServiceWorkerModule.register('ngsw-worker.js', { enabled: true }),
        ArtemisSharedModule,
        ArtemisCoreModule,
        ArtemisHomeModule,
        ArtemisAppRoutingModule,
        GuidedTourModule,
        ArtemisLegalModule,
        ArtemisCoursesModule,
        ArtemisSystemNotificationModule,
        ArtemisComplaintsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        UserSettingsModule,
        ThemeModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [
        JhiMainComponent,
        NavbarComponent,
        ErrorComponent,
        OrionOutdatedComponent,
        PageRibbonComponent,
        ActiveMenuDirective,
        FooterComponent,
        NotificationPopupComponent,
        NotificationSidebarComponent,
        SystemNotificationComponent,
        LoadingNotificationComponent,
    ],
    bootstrap: [JhiMainComponent],
})
export class ArtemisAppModule {}
