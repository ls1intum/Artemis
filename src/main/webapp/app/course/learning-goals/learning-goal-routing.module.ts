import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CreateLearningGoalComponent } from 'app/course/learning-goals/create-learning-goal/create-learning-goal.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';

const routes: Routes = [
    {
        path: ':courseId/goals/create',
        component: CreateLearningGoalComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.learningGoal.createLearningGoal.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/goals/:goalId/edit',
        component: CreateLearningGoalComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.learningGoal.editLearningGoal.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/goal-management',
        component: LearningGoalManagementComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.learningGoal.manageLearningGoals.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisLearningGoalsRoutingModule {}
