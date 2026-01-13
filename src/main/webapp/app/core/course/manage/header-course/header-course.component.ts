import { Component, HostListener, computed, effect, inject, input, signal, untracked } from '@angular/core';
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
export class HeaderCourseComponent {
    protected router = inject(Router);

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    public readonly course = input<Course>(undefined!);

    readonly courseColor = computed(() => this.course()?.color || ARTEMIS_DEFAULT_COLOR);
    readonly contentColor = computed(() => getContrastingTextColor(this.courseColor()));

    readonly longDescriptionShown = signal(false);
    readonly enableShowMore = signal(false);
    readonly courseDescription = signal<string | undefined>(undefined);

    faArrowDown = faArrowDown;

    constructor() {
        effect(() => {
            // Track course input changes
            const course = this.course();
            if (course) {
                untracked(() => this.adjustCourseDescription());
            }
        });
    }

    @HostListener('window:resize')
    onResize() {
        this.adjustCourseDescription();
    }

    /**
     * Toggle between showing the long and abbreviated course description
     */
    toggleCourseDescription() {
        this.longDescriptionShown.update((shown) => !shown);
        this.adjustCourseDescription();
    }

    /**
     * Adjusts the course description and shows toggle buttons (if it is too long) based on the current window width
     */
    adjustCourseDescription() {
        const shortDescriptionLength = window.innerWidth / (this.course()?.courseIconPath ? 3.6 : 3.4);
        const course = this.course();
        if (course && course.description) {
            this.enableShowMore.set(course.description.length > shortDescriptionLength);
            if (this.enableShowMore() && !this.longDescriptionShown()) {
                this.courseDescription.set(course.description.slice(0, shortDescriptionLength) + '...');
            } else {
                this.courseDescription.set(course.description);
            }
        }
    }
}
