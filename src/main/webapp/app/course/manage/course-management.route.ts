/* eslint-disable max-len */
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementComponent } from './course-management.component';
import { CourseDetailComponent } from './detail/course-detail.component';
import { CourseUpdateComponent } from './course-update.component';
import { CourseManagementExercisesComponent } from './course-management-exercises.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';
import { CompetencyManagementComponent } from 'app/course/competencies/competency-management/competency-management.component';
import { CreateCompetencyComponent } from 'app/course/competencies/create-competency/create-competency.component';
import { EditCompetencyComponent } from 'app/course/competencies/edit-competency/edit-competency.component';
import { CourseManagementStatisticsComponent } from './course-management-statistics.component';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { isOrion } from 'app/shared/orion/orion';
import { OrionCourseManagementExercisesComponent } from 'app/orion/management/orion-course-management-exercises.component';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { CourseGroupMembershipComponent } from 'app/course/manage/course-group-membership/course-group-membership.component';
import { TutorialGroupManagementResolve } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-management-resolve.service';
import { TutorialGroupsChecklistComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CreateTutorialGroupsConfigurationComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component';
import { CourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/course-lti-configuration.component';
import { EditCourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/edit-course-lti-configuration.component';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';

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
        path: '',
        component: CourseManagementTabBarComponent,
        children: [
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
                path: ':courseId/tutorial-groups',
                resolve: {
                    course: TutorialGroupManagementResolve,
                },
                loadChildren: () =>
                    import('app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-management.module').then((m) => m.ArtemisTutorialGroupsManagementModule),
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
                path: ':courseId/exams',
                loadChildren: () => import('../../exam/manage/exam-management.module').then((m) => m.ArtemisExamManagementModule),
            },
            {
                path: ':courseId/tutorial-groups-checklist',
                component: TutorialGroupsChecklistComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.pages.checklist.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/create-tutorial-groups-configuration',
                component: CreateTutorialGroupsConfigurationComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.pages.createTutorialGroupsConfiguration.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/lti-configuration',
                component: CourseLtiConfigurationComponent,
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.lti.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/lti-configuration/edit',
                component: EditCourseLtiConfigurationComponent,
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.lti.home.title',
                },
                canActivate: [UserRouteAccessService],
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
                            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
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
                        path: 'competency-management',
                        component: CompetencyManagementComponent,
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.competency.manageCompetencies.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        // Create a new path without a component defined to prevent the CompetencyManagementComponent from being always rendered
                        path: 'competency-management',
                        data: {
                            pageTitle: 'artemisApp.competency.manageCompetencies.title',
                        },
                        children: [
                            {
                                path: 'create',
                                component: CreateCompetencyComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.createCompetency.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: ':competencyId/edit',
                                component: EditCompetencyComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.editCompetency.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                        ],
                    },
                ],
            },
        ],
    },
];
