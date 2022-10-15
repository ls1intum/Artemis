// tslint:disable:max-line-length
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementComponent } from './course-management.component';
import { CourseDetailComponent } from './detail/course-detail.component';
import { CourseUpdateComponent } from './course-update.component';
import { CourseManagementExercisesComponent } from './course-management-exercises.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';
import { CreateLearningGoalComponent } from 'app/course/learning-goals/create-learning-goal/create-learning-goal.component';
import { EditLearningGoalComponent } from 'app/course/learning-goals/edit-learning-goal/edit-learning-goal.component';
import { CourseParticipantScoresComponent } from 'app/course/course-participant-scores/course-participant-scores.component';
import { CourseManagementStatisticsComponent } from './course-management-statistics.component';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { isOrion } from 'app/shared/orion/orion';
import { OrionCourseManagementExercisesComponent } from 'app/orion/management/orion-course-management-exercises.component';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { CourseGroupMembershipComponent } from 'app/course/manage/course-group-membership/course-group-membership.component';

export const courseManagementState: Routes = [
    {
        path: '',
        component: CourseManagementComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: CourseUpdateComponent,
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'global.generic.create',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId',
        component: CourseDetailComponent,
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/participant-scores',
        component: CourseParticipantScoresComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.participantScores.pageTitle',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/grading-system',
        component: GradingSystemComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.gradingSystem',
        },
        canActivate: [UserRouteAccessService],
        loadChildren: () => import('app/grading-system/grading-system.module').then((m) => m.GradingSystemModule),
    },
    {
        path: ':courseId/plagiarism-cases',
        loadChildren: () => import('../plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.module').then((m) => m.ArtemisPlagiarismCasesInstructorViewModule),
    },
    {
        path: ':courseId/exams/:examId/plagiarism-cases',
        loadChildren: () => import('../plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.module').then((m) => m.ArtemisPlagiarismCasesInstructorViewModule),
    },
    {
        path: ':courseId/tutorial-groups-management',
        resolve: {
            course: CourseManagementResolve,
        },
        loadChildren: () => import('app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.module').then((m) => m.ArtemisTutorialGroupsManagementModule),
    },
    {
        // Create a new path without a component defined to prevent resolver caching and the CourseDetailComponent from being always rendered
        path: ':courseId',
        resolve: {
            course: CourseManagementResolve,
        },
        children: [
            {
                path: 'exercises',
                component: !isOrion ? CourseManagementExercisesComponent : OrionCourseManagementExercisesComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA, Authority.ADMIN],
                    pageTitle: 'artemisApp.course.exercises',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'course-statistics',
                component: CourseManagementStatisticsComponent,
                data: {
                    authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.courseStatistics.statistics',
                    breadcrumbLabelVariable: '',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'edit',
                component: CourseUpdateComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.course.home.editLabel',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'groups/:courseGroup',
                component: CourseGroupMembershipComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.userManagement.groups',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'ratings',
                component: RatingListComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.ratingList.pageTitle',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'goal-management',
                component: LearningGoalManagementComponent,
                data: {
                    authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
                    pageTitle: 'artemisApp.learningGoal.manageLearningGoals.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                // Create a new path without a component defined to prevent the LearningGoalManagementComponent from being always rendered
                path: 'goal-management',
                data: {
                    pageTitle: 'artemisApp.learningGoal.manageLearningGoals.title',
                },
                children: [
                    {
                        path: 'create',
                        component: CreateLearningGoalComponent,
                        data: {
                            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
                            pageTitle: 'artemisApp.learningGoal.createLearningGoal.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: ':learningGoalId/edit',
                        component: EditLearningGoalComponent,
                        data: {
                            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
                            pageTitle: 'artemisApp.learningGoal.editLearningGoal.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                ],
            },
        ],
    },
];
