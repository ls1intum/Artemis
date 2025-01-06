import './polyfills';
import 'app/shared/util/array.extension';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import { ProdConfig } from './core/config/prod.config';
import { MonacoConfig } from 'app/core/config/monaco.config';
import { BrowserModule, bootstrapApplication } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ServiceWorkerModule } from '@angular/service-worker';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisSystemNotificationModule } from 'app/shared/notification/system-notification/system-notification.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { UserSettingsModule } from 'app/shared/user-settings/user-settings.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { JhiMainComponent } from './shared/layouts/main/main.component';
import { importProvidersFrom } from '@angular/core';

ProdConfig();
MonacoConfig();

bootstrapApplication(JhiMainComponent, {
    providers: [
        importProvidersFrom(
            BrowserModule,
            // This enables service worker (PWA)
            ServiceWorkerModule.register('ngsw-worker.js', { enabled: true }),
            ArtemisSharedModule,
            ArtemisCoreModule,
            ArtemisAppRoutingModule,
            GuidedTourModule,
            ArtemisSystemNotificationModule,
            ArtemisComplaintsModule,
            ArtemisHeaderExercisePageWithDetailsModule,
            UserSettingsModule,
            ArtemisSharedComponentModule,
            ScrollingModule,
        ),
        provideAnimations(),
    ],
})
    .then(() => {})
    .catch((err) => console.error(err));
