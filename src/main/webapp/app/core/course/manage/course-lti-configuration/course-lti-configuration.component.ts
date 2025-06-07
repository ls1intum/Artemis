import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faExclamationTriangle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavLink, NgbNavLinkBase, NgbNavOutlet, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';

@Component({
    selector: 'jhi-course-lti-configuration',
    templateUrl: './course-lti-configuration.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        NgbNav,
        NgbNavItem,
        NgbNavLink,
        NgbNavLinkBase,
        NgbNavContent,
        NgbTooltip,
        NgbNavOutlet,
        ArtemisTranslatePipe,
        CopyToClipboardButtonComponent,
        HelpIconComponent,
        SortDirective,
        SortByDirective,
    ],
})
export class CourseLtiConfigurationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private sortService = inject(SortService);
    private courseManagementService = inject(CourseManagementService);

    protected readonly Object = Object;

    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    exercises: Exercise[];
    lectures: Lecture[];

    activeTab = 1;

    predicate = 'type';
    reverse = false;
    showAdvancedSettings = false;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;

    /**
     * Gets the configuration for the course encoded in the route and fetches the exercises
     */
    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.onlineCourseConfiguration = course.onlineCourseConfiguration;
                this.courseManagementService.findWithExercisesAndLecturesAndCompetencies(course.id).subscribe((findWithExercisesAndLecturesResult) => {
                    if (findWithExercisesAndLecturesResult?.body?.exercises) {
                        this.exercises = findWithExercisesAndLecturesResult.body.exercises;
                    }
                    if (findWithExercisesAndLecturesResult?.body?.lectures) {
                        this.lectures = findWithExercisesAndLecturesResult.body.lectures;
                    }
                });
            }
        });
    }

    /**
     * Gets the LTI 1.3 launch url for an exercise
     */
    getExerciseLti13LaunchUrl(exercise: Exercise): string {
        return `${location.origin}/courses/${this.course.id}/exercises/${exercise.id}`; // Needs to match url in Lti13Service
    }

    getLectureLti13LaunchUrl(lecture: Lecture): string {
        return `${location.origin}/courses/${this.course.id}/lectures/${lecture.id}`;
    }

    sortRows() {
        this.sortService.sortByProperty(this.exercises, this.predicate, this.reverse);
    }

    /**
     * Returns true if any required LTI 1.3 fields are missing
     */
    missingLti13ConfigurationField(): boolean {
        return !this.onlineCourseConfiguration.ltiPlatformConfiguration;
    }

    toggleAdvancedSettings() {
        this.showAdvancedSettings = !this.showAdvancedSettings;
    }
}
