import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-course-redirect',
    template: '',
})
export class CourseManagementExerciseTypeRedirectComponent implements OnInit {
    constructor(private route: ActivatedRoute, private router: Router, private exerciseService: ExerciseService) {}

    /**
     * Redirect to the correct route for the exercise type.
     * This is currently used when navigating from the teams page of an exercise back to the details page using the breadcrumb links as the teams page
     * is located in course-management/:cId/exercises/:eId, and the breadcrumb generator can't know that this is wrong
     */
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
