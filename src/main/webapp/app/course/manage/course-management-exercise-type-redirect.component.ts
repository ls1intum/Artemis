import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-course-redirect',
    template: '',
})
export class CourseManagementExerciseTypeRedirectComponent {
    constructor(private route: ActivatedRoute, private router: Router, private exerciseService: ExerciseService) {
        this.route.params.subscribe((params) => {
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                if (!exerciseResponse?.body?.type) {
                    return;
                }
                const typeSegement = exerciseResponse.body.type + '-exercises';
                router.navigate(['course-management', params['courseId'], typeSegement, exerciseResponse.body.id], { replaceUrl: true });
            });
        });
    }
}
