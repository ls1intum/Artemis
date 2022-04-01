import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-course-redirect',
    template: '',
})
export class CourseManagementExerciseTypeRedirectComponent implements OnInit {
    constructor(private route: ActivatedRoute, private router: Router, private exerciseService: ExerciseService) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                if (!exerciseResponse?.body?.type) {
                    return;
                }
                const typeSegement = exerciseResponse.body.type + '-exercises';
                this.router.navigate(['course-management', params['courseId'], typeSegement, exerciseResponse.body.id], { replaceUrl: true });
            });
        });
    }
}
