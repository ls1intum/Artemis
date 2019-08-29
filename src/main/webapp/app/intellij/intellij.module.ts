import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IntellijButtonComponent } from 'app/intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { MomentModule } from 'ngx-moment';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

export function initJavaBridge(bridge: JavaBridgeService) {
    return () => bridge.initBridge();
}

@NgModule({
    declarations: [IntellijButtonComponent],
    imports: [
        CommonModule,
        FontAwesomeModule,
        ArtemisProgrammingExerciseModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
    ],
    exports: [IntellijButtonComponent],
    providers: [JavaBridgeService, { provide: APP_INITIALIZER, useFactory: initJavaBridge, deps: [JavaBridgeService], multi: true }],
})
export class IntellijModule {}
