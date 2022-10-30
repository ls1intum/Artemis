import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';

@Component({
    selector: 'jhi-course-lti-configuration',
    templateUrl: './course-lti-configuration.component.html',
})
export class CourseLtiConfigurationComponent implements OnInit {
    course: Course;
    onlineCourseConfiguration: OnlineCourseConfiguration;
    requireExistingUser = true;
    lookupUserByEmail = true;

    wasCopiedKey = '';

    constructor(private route: ActivatedRoute) {}

    /**
     * Opens the configuration for the course encoded in the route
     */
    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.onlineCourseConfiguration = course.onlineCourseConfiguration;
                console.log('course', course);
            }
        });
    }

    /**
     * Gets the dynamic registration url
     */
    getDynamicRegistrationUrl(): string {
        return `${SERVER_API_URL}/lti/dynamic-registration/${this.course.id}`;
    }

    /**
     * Gets the formatted custom parameters
     */
    getFormattedCustomParameters(): string {
        return `require_existing_user=${this.requireExistingUser}\n` + `lookup_user_by_email=${this.lookupUserByEmail}`;
    }

    /**
     * set wasCopied for 3 seconds on success for the received key
     */
    onCopyFinished = (successful: boolean, key: string): void => {
        if (successful) {
            this.wasCopiedKey = key;
            setTimeout(() => {
                this.wasCopiedKey = '';
            }, 3000);
        }
    };
}
