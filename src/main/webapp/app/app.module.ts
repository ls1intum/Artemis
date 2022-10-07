import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ServiceWorkerModule } from '@angular/service-worker';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ArtemisSystemNotificationModule } from 'app/shared/notification/system-notification/system-notification.module';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { PageRibbonComponent } from 'app/shared/layouts/profiles/page-ribbon.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SystemNotificationComponent } from 'app/shared/notification/system-notification/system-notification.component';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { ArtemisLegalModule } from 'app/core/legal/legal.module';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { ErrorComponent } from 'app/shared/layouts/error/error.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHomeModule } from 'app/home/home.module';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { LoadingNotificationComponent } from 'app/shared/notification/loading-notification/loading-notification.component';
import { NotificationPopupComponent } from 'app/shared/notification/notification-popup/notification-popup.component';
import { UserSettingsModule } from 'app/shared/user-settings/user-settings.module';
import { ThemeModule } from 'app/core/theme/theme.module';
import { LogoutComponent } from './shared/layouts/navbar/logout/logout.component';

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
        LogoutComponent,
    ],
    bootstrap: [JhiMainComponent],
})
export class ArtemisAppModule {}
