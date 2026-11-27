import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons/faExclamationTriangle';
import { faWrench } from '@fortawesome/free-solid-svg-icons/faWrench';
import { SortService } from 'app/foundation/service/sort.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CopyToClipboardButtonComponent } from 'app/shared-ui/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiTableDirective, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular/table-directive';
import { TumAetUiTableLayoutComponent } from '@tumaet/ui-angular/table-layout';
import { TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabPanelComponent, TumAetUiTabPanelsComponent, TumAetUiTabsComponent } from '@tumaet/ui-angular/tabs';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';

@Component({
    selector: 'jhi-course-lti-configuration',
    templateUrl: './course-lti-configuration.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTabPanelsComponent,
        TumAetUiTabPanelComponent,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
        CopyToClipboardButtonComponent,
        HelpIconComponent,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
    ],
})
export class CourseLtiConfigurationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private sortService = inject(SortService);
    private courseManagementService = inject(CourseManagementService);

    protected readonly Object = Object;

    course = signal<Course | undefined>(undefined);
    onlineCourseConfiguration = signal<OnlineCourseConfiguration | undefined>(undefined);
    exercises = signal<Exercise[]>([]);
    lectures = signal<Lecture[]>([]);

    activeTab = signal<number | string>(1);

    predicate = signal('type');
    reverse = signal(false);
    showAdvancedSettings = signal(false);

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;

    /**
     * Gets the configuration for the course encoded in the route and fetches the exercises
     */
    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
                this.onlineCourseConfiguration.set(course.onlineCourseConfiguration);
                this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(course.id).subscribe((findWithExercisesAndLecturesResult) => {
                    if (findWithExercisesAndLecturesResult?.body?.exercises) {
                        this.exercises.set(findWithExercisesAndLecturesResult.body.exercises);
                    }
                    if (findWithExercisesAndLecturesResult?.body?.lectures) {
                        this.lectures.set(findWithExercisesAndLecturesResult.body.lectures);
                    }
                });
            }
        });
    }

    /**
     * Gets the LTI 1.3 launch url for an exercise
     */
    getExerciseLti13LaunchUrl(exercise: Exercise): string {
        return `${location.origin}/courses/${this.course()?.id}/exercises/${exercise.id}`; // Needs to match url in Lti13Service
    }

    getLectureLti13LaunchUrl(lecture: Lecture): string {
        return `${location.origin}/courses/${this.course()?.id}/lectures/${lecture.id}`;
    }

    onSort(event: TumAetUiTableSortEvent): void {
        this.predicate.set(event.field);
        this.reverse.set(event.order === 1);
        this.sortRows();
        const sortedLectures = [...this.lectures()];
        this.sortService.sortByProperty(sortedLectures, this.predicate(), this.reverse());
        this.lectures.set(sortedLectures);
    }

    sortRows() {
        const sortedExercises = [...this.exercises()];
        this.sortService.sortByProperty(sortedExercises, this.predicate(), this.reverse());
        this.exercises.set(sortedExercises);
    }

    /**
     * Returns true if any required LTI 1.3 fields are missing
     */
    missingLti13ConfigurationField(): boolean {
        return !this.onlineCourseConfiguration()?.ltiPlatformConfiguration;
    }

    toggleAdvancedSettings() {
        this.showAdvancedSettings.update((value) => !value);
    }
}
