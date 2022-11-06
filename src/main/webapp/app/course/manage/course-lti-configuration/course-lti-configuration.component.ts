import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise } from 'app/entities/exercise.model';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-course-lti-configuration',
    templateUrl: './course-lti-configuration.component.html',
})
export class CourseLtiConfigurationComponent implements OnInit {
    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    exercises: Exercise[];
    requireExistingUser = true;
    lookupUserByEmail = true;

    activeTab = 1;

    predicate = 'type';
    reverse = false;

    // Icons
    faSort = faSort;

    constructor(private route: ActivatedRoute, private sortService: SortService, private courseManagementService: CourseManagementService) {}

    /**
     * Opens the configuration for the course encoded in the route
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
     * Gets the dynamic registration url
     */
    getDynamicRegistrationUrl(): string {
        return `${SERVER_API_URL}/lti/dynamic-registration/${this.course.id}`; // Needs to match url in lti.route
    }

    /**
     * Gets the deep linking url
     */
    getDeepLinkingUrl(): string {
        return `${SERVER_API_URL}/api/lti13/deep-linking/${this.course.id}`; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the keyset url
     */
    getKeysetUrl(): string {
        return `${SERVER_API_URL}/.well-known/jwks.json`; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the LTI 1.0 launch url for an exercise
     */
    getExerciseLti10LaunchUrl(exercise: Exercise): string {
        return `${SERVER_API_URL}/api/lti/launch/${exercise.id}`; // Needs to match url in LtiResource
    }

    /**
     * Gets the LTI 1.3 launch url for an exercise
     */
    getExerciseLti13LaunchUrl(exercise: Exercise): string {
        return `${SERVER_API_URL}/courses/${this.course.id}/exercises/${exercise.id}`; // Needs to match url in Lti13Service
    }

    /**
     * Gets the formatted custom parameters
     */
    getFormattedCustomParameters(): string {
        return `require_existing_user=${this.requireExistingUser}\n` + `lookup_user_by_email=${this.lookupUserByEmail}`;
    }

    sortRows() {
        this.sortService.sortByProperty(this.exercises, this.predicate, this.reverse);
    }
}
