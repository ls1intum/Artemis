import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { GocastCourseBindingComponent } from './gocast-course-binding.component';

@Component({
    selector: 'jhi-gocast-course-binding-page',
    template: '<jhi-gocast-course-binding [courseId]="courseId()" />',
    imports: [GocastCourseBindingComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GocastCourseBindingPageComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly routeCourseId = toSignal(this.route.paramMap.pipe(map((params) => Number(params.get('courseId')))), {
        initialValue: Number(this.route.snapshot.paramMap.get('courseId')),
    });

    readonly courseId = computed(() => this.routeCourseId());
}
