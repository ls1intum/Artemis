import { Component, Signal, TemplateRef, computed, inject, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';

/**
 * Shared title bar component used by both course overview and course management components
 */
@Component({
    selector: 'jhi-course-title-bar',
    templateUrl: './course-title-bar.component.html',
    styleUrls: ['./course-title-bar.component.scss'],
    imports: [NgTemplateOutlet, CourseTitleBarTitleComponent],
})
export class CourseTitleBarComponent {
    readonly displayStyle = computed(() => (this.isExamStarted() ? 'none' : 'flex'));
    pageTitle = input('');
    isExamStarted = input(false);

    private courseTitleBarService = inject(CourseTitleBarService);
    readonly customTitleTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.courseTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<unknown> | undefined> = computed(() => this.courseTitleBarService.actionsTemplate());
}
