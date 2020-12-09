import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CreateLearningGoalComponent } from 'app/course/learning-goals/create-learning-goal/create-learning-goal.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';
import { EditLearningGoalComponent } from 'app/course/learning-goals/edit-learning-goal/edit-learning-goal.component';
import { CourseResolve } from 'app/course/manage/course-management.route';

const routes: Routes = [
    {
        path: ':courseId',
        resolve: {
            course: CourseResolve,
        },
        data: {
            breadcrumbLabelVariable: 'course.title',
        },
        children: [
            {
                path: 'goal-management',
                component: LearningGoalManagementComponent,
                data: {
                    authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
                    // Overwrite the parent value to be empty
                    breadcrumbLabelVariable: '',
                    pageTitle: 'artemisApp.learningGoal.manageLearningGoals.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                // Create a new path without a component defined to prevent the LearningGoalManagementComponent from being always rendered
                path: 'goal-management',
                data: {
                    // Overwrite the parent value to be empty
                    breadcrumbLabelVariable: '',
                    pageTitle: 'artemisApp.learningGoal.manageLearningGoals.title',
                },
                children: [
                    {
                        path: 'create',
                        component: CreateLearningGoalComponent,
                        data: {
                            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
                            breadcrumbLabelVariable: '',
                            pageTitle: 'artemisApp.learningGoal.createLearningGoal.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: ':learningGoalId/edit',
                        component: EditLearningGoalComponent,
                        data: {
                            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
                            breadcrumbLabelVariable: '',
                            pageTitle: 'artemisApp.learningGoal.editLearningGoal.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                ],
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisLearningGoalsRoutingModule {}
