import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { Leaderboard } from 'app/core/course/overview/course-training/leaderboard/leaderboard';

@Component({
    selector: 'jhi-course-practice',
    imports: [ButtonComponent, Leaderboard, Leaderboard],
    templateUrl: './course-training.component.html',
})
export class CourseTrainingComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);

    public navigateToTraining(): void {
        this.router.navigate(['courses', this.courseId(), 'training', 'quiz']);
    }
}
