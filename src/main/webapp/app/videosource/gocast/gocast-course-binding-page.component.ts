import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { GocastCourseBindingComponent } from './gocast-course-binding.component';

/**
 * Route wrapper for the TUM Live course binding management page.
 * Resolves the courseId from the route and renders GocastCourseBindingComponent.
 *
 * Route: /course-management/:courseId/gocast-binding
 * Authorities: AT_LEAST_INSTRUCTOR
 */
@Component({
    selector: 'jhi-gocast-course-binding-page',
    template: `
        <div class="container-fluid">
            <div class="row">
                <div class="col-12 col-lg-8">
                    <h2 jhiTranslate="artemisApp.gocast.binding.pageTitle"></h2>
                    @if (courseId()) {
                        <jhi-gocast-course-binding [courseId]="courseId()!" />
                    }
                </div>
            </div>
        </div>
    `,
    imports: [TranslateDirective, GocastCourseBindingComponent],
})
export class GocastCourseBindingPageComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);

    courseId = signal<number | undefined>(undefined);

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            const id = Number(params['courseId']);
            if (id) {
                this.courseId.set(id);
            }
        });
    }
}
