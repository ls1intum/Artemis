import { Component, HostListener, Input, OnChanges } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-header-course',
    templateUrl: './header-course.component.html',
    styleUrls: ['./header-course.component.scss'],
})
export class HeaderCourseComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly CachingStrategy = CachingStrategy;

    @Input() public course: Course;

    public courseDescription?: string;
    public enableShowMore = false;
    public longDescriptionShown = false;

    constructor(private router: Router) {}

    ngOnChanges() {
        this.adjustCourseDescription();
    }

    @HostListener('window:resize')
    onResize() {
        this.adjustCourseDescription();
    }

    /**
     * Toggle between showing the long and abbreviated course description
     */
    toggleCourseDescription() {
        this.longDescriptionShown = !this.longDescriptionShown;
        this.adjustCourseDescription();
    }

    /**
     * Adjusts the course description and shows toggle buttons (if it is too long) based on the current window width
     */
    adjustCourseDescription() {
        const shortDescriptionLength = window.innerWidth / (this.course.courseIcon ? 3.6 : 3.4);
        if (this.course && this.course.description) {
            this.enableShowMore = this.course.description.length > shortDescriptionLength;
            if (this.enableShowMore && !this.longDescriptionShown) {
                this.courseDescription = this.course.description.slice(0, shortDescriptionLength) + '…';
            } else {
                this.courseDescription = this.course.description;
            }
        }
    }

    shouldShowGoToCourseManagementButton() {
        const courseManagementPage = this.router.url.startsWith('/course-management');
        return !courseManagementPage && this.course.isAtLeastTutor;
    }

    redirectToCourseManagement() {
        this.router.navigate(['course-management', this.course.id]);
    }
}
