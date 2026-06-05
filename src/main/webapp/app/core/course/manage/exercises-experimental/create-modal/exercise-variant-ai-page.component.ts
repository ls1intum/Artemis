import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { ExerciseVariantAiEditorComponent } from './exercise-variant-ai-editor.component';

@Component({
    selector: 'jhi-exercise-variant-ai-page',
    templateUrl: './exercise-variant-ai-page.component.html',
    imports: [ExerciseVariantAiEditorComponent, FaIconComponent],
})
export class ExerciseVariantAiPageComponent implements OnInit {
    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly courseId = signal<number | undefined>(undefined);

    protected readonly faArrowLeft = faArrowLeft;

    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly mockService = inject(ExerciseManagementMockService);

    ngOnInit(): void {
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId.set(courseId || undefined);
        const found = this.mockService.getExercises().find((e) => e.id === exerciseId);
        this.exercise.set(found);
    }

    onVariantAdded(variant: Exercise): void {
        // Navigate back to the exercise list after creation
        const cId = this.courseId();
        if (cId !== undefined) {
            this.router.navigate(['/course-management', cId, 'exercises', 'experimental']);
        }
    }

    onClose(): void {
        const cId = this.courseId();
        if (cId !== undefined) {
            this.router.navigate(['/course-management', cId, 'exercises', 'experimental']);
        }
    }
}
