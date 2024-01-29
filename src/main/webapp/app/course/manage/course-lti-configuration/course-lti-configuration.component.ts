import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { faExclamationTriangle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-course-lti-configuration',
    templateUrl: './course-lti-configuration.component.html',
})
export class CourseLtiConfigurationComponent implements OnInit {
    protected readonly Object = Object;

    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    exercises: Exercise[];

    activeTab = 1;
    ltiVersions = {
        'artemisApp.lti.version10': '',
        'artemisApp.lti.version13': '',
    };

    selectedLtiVersionKey: string;
    predicate = 'type';
    reverse = false;
    showAdvancedSettings = false;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;

    constructor(
        private route: ActivatedRoute,
        private sortService: SortService,
        private courseManagementService: CourseManagementService,
    ) {
        this.selectedLtiVersionKey = 'artemisApp.lti.version13';
    }

    /**
     * Gets the configuration for the course encoded in the route and fetches the exercises
     */
    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.onlineCourseConfiguration = course.onlineCourseConfiguration;
                this.courseManagementService.findWithExercises(course.id).subscribe((findWithExercisesResult) => {
                    if (findWithExercisesResult?.body?.exercises) {
                        this.exercises = findWithExercisesResult.body.exercises;
                    }
                });
            }
        });
    }

    /**
     * Gets the LTI 1.0 launch url for an exercise
     */
    getExerciseLti10LaunchUrl(exercise: Exercise): string {
        return `${location.origin}/api/public/lti/launch/${exercise.id}`; // Needs to match url in LtiResource
    }

    /**
     * Gets the LTI 1.3 launch url for an exercise
     */
    getExerciseLti13LaunchUrl(exercise: Exercise): string {
        return `${location.origin}/courses/${this.course.id}/exercises/${exercise.id}`; // Needs to match url in Lti13Service
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
