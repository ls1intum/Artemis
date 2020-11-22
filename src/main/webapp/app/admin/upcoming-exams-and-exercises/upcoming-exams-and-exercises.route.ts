import { Route } from '@angular/router';
import { UpcomingExamsAndExercisesComponent } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';

export const upcomingExamsAndExercisesRoute: Route = {
    path: 'upcoming-exams-and-exercises',
    component: UpcomingExamsAndExercisesComponent,
    data: {
        pageTitle: 'artemisApp.upcomingExamsAndExercises.upcomingExamsAndExercises',
    },
};
