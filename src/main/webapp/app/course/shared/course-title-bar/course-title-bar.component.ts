import { Component, Signal, TemplateRef, computed, inject, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';

/**
 * Shared title bar component used by both course overview and course management components
 */
@Component({
    selector: 'jhi-course-title-bar',
    // The global rules of layout.css pin the controls that pages project into this bar to the height of the bar.
    host: { class: 'title-bar-controls' },
    templateUrl: './course-title-bar.component.html',
    styleUrls: ['./course-title-bar.component.css'],
    imports: [NgTemplateOutlet, CourseTitleBarTitleComponent],
})
export class CourseTitleBarComponent {
    private courseTitleBarService = inject(CourseTitleBarService);

    readonly displayStyle = computed(() => (this.isExamStarted() ? 'none' : 'flex'));
    pageTitle = input('');
    isExamStarted = input(false);

    readonly customTitleTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.courseTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.courseTitleBarService.actionsTemplate());
    readonly customToolbarTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.courseTitleBarService.toolbarTemplate());
}
