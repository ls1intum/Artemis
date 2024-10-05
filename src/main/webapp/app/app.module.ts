import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ServiceWorkerModule } from '@angular/service-worker';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { NotificationPopupComponent } from 'app/shared/notification/notification-popup/notification-popup.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ErrorComponent } from 'app/shared/layouts/error/error.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { UserSettingsModule } from 'app/shared/user-settings/user-settings.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FaIconLibrary } from '@fortawesome/angular-fontawesome';
import { artemisIconPack } from 'src/main/webapp/content/icons/icons';
import { ScrollingModule } from '@angular/cdk/scrolling';

// NOTE: this module should only include the most important modules for normal users, all course management, admin and account functionality should be lazy loaded if possible
@NgModule({
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        // This enables service worker (PWA)
        ServiceWorkerModule.register('ngsw-worker.js', { enabled: true }),
        ArtemisSharedModule,
        ArtemisCoreModule,
        ArtemisAppRoutingModule,
        GuidedTourModule,
        ArtemisComplaintsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        UserSettingsModule,
        ArtemisSharedComponentModule,
        ScrollingModule,
        NotificationPopupComponent,
    ],
    declarations: [ErrorComponent, OrionOutdatedComponent],
})
export class ArtemisAppModule {
    constructor(library: FaIconLibrary) {
        library.addIconPacks(artemisIconPack);
    }
}
