import { Component, ViewEncapsulation, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { ProgrammingExerciseInstructorExerciseDownloadComponent } from 'app/programming/shared/actions/instructor-exercise-download/programming-exercise-instructor-exercise-download.component';
import { ProgrammingExerciseResetButtonDirective } from 'app/programming/manage/reset/button/programming-exercise-reset-button.directive';
import { ProgrammingExerciseInstructorExerciseSharingComponent } from 'app/programming/shared/actions/programming-exercise-instructor-exercise-sharing.component';
import { ProgrammingExerciseDetailComponent } from 'app/programming/manage/detail/programming-exercise-detail.component';
import { ExerciseVariantAiModalDispatcherComponent } from 'app/core/course/manage/exercises-experimental/create-modal/exercise-variant-ai-modal-dispatcher.component';

@Component({
    selector: 'jhi-programming-exercise-detail-experimental',
    templateUrl: './programming-exercise-detail-experimental.component.html',
    styleUrls: ['../../../../../programming/manage/detail/programming-exercise-detail.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        RouterLink,
        FaIconComponent,
        FeatureToggleLinkDirective,
        NgbTooltip,
        ProgrammingExerciseInstructorExerciseDownloadComponent,
        FeatureToggleDirective,
        ProgrammingExerciseResetButtonDirective,
        DeleteButtonDirective,
        ExerciseDetailStatisticsComponent,
        DetailOverviewListComponent,
        ArtemisTranslatePipe,
        FeatureOverlayComponent,
        ProgrammingExerciseInstructorExerciseSharingComponent,
        ExerciseVariantAiModalDispatcherComponent,
    ],
})
export class ProgrammingExerciseDetailExperimentalComponent extends ProgrammingExerciseDetailComponent {
    protected readonly faRobot = faRobot;
    readonly aiVariantModalVisible = signal(false);
}
