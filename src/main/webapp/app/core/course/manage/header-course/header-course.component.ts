import { Component, HostListener, Input, OnChanges, inject } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ImageComponent } from 'app/shared/image/image.component';
import { Router, RouterLink } from '@angular/router';
import { faArrowDown } from '@fortawesome/free-solid-svg-icons';
import { NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { getContrastingTextColor } from 'app/shared/util/color.utils';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-header-course',
    templateUrl: './header-course.component.html',
    styleUrls: ['./header-course.component.scss'],
    imports: [NgStyle, FaIconComponent, RouterLink, ImageComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class HeaderCourseComponent implements OnChanges {
    protected router = inject(Router);

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    @Input() public course: Course;

    public courseColor: string;
    public contentColor: string;
    public courseDescription?: string;
    public enableShowMore = false;
    public longDescriptionShown = false;

    faArrowDown = faArrowDown;

    ngOnChanges() {
        this.courseColor = this.course.color || ARTEMIS_DEFAULT_COLOR;
        this.contentColor = getContrastingTextColor(this.courseColor);
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
        const shortDescriptionLength = window.innerWidth / (this.course.courseIconPath ? 3.6 : 3.4);
        const description = this.course?.extendedSettings?.description;
        if (this.course && description) {
            this.enableShowMore = description.length > shortDescriptionLength;
            if (this.enableShowMore && !this.longDescriptionShown) {
                this.courseDescription = description.slice(0, shortDescriptionLength) + '…';
            } else {
                this.courseDescription = description;
            }
        }
    }
}
