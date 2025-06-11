import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { throwError } from 'rxjs';

@Component({
    selector: 'jhi-course-practice',
    imports: [ButtonComponent],
    templateUrl: './course-practice.component.html',
})
export class CoursePracticeComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    paramsSignal = toSignal(this.route.parent?.params ?? throwError(() => new Error('parent.params fehlt')));
    //paramsSignal = toSignal(this.route.parent?.params);
    courseId = computed(() => this.paramsSignal()?.['courseId']);

    public navigateToPractice(): void {
        this.router.navigate(['courses', this.courseId(), 'practice', 'quiz']);
    }
}
