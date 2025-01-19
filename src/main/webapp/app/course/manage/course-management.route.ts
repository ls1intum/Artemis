import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { TutorialGroupManagementResolve } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-management-resolve.service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { LocalCIGuard } from 'app/localci/localci-guard.service';
import { IrisGuard } from 'app/iris/iris-guard.service';
import { FaqResolve } from 'app/faq/faq.routes';
import { examManagementRoute } from 'app/exam/manage/exam-management.route';

export const courseManagementState: Routes = [
    {
        path: '',
        loadComponent: () => import('./course-management.component').then((m) => m.CourseManagementComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        loadComponent: () => import('./course-update.component').then((m) => m.CourseUpdateComponent),
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'global.generic.create',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: '',
        loadComponent: () => import('app/course/manage/course-management-tab-bar/course-management-tab-bar.component').then((m) => m.CourseManagementTabBarComponent),
        children: [
            {
                path: ':courseId',
                loadComponent: () => import('./detail/course-detail.component').then((m) => m.CourseDetailComponent),
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
                loadComponent: () => import('app/grading-system/grading-system.component').then((m) => m.GradingSystemComponent),
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.course.gradingSystem',
                },
                canActivate: [UserRouteAccessService],
                loadChildren: () => import('app/grading-system/grading-system.module').then((m) => m.GradingSystemModule),
            },
            {
                path: ':courseId/iris-settings',
                loadChildren: () =>
                    import('app/iris/settings/iris-course-settings-update/iris-course-settings-update-routing.module').then((m) => m.IrisCourseSettingsUpdateRoutingModule),
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
                children: examManagementRoute,
            },
            {
                path: ':courseId/tutorial-groups-checklist',
                loadComponent: () =>
                    import('app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-checklist/tutorial-groups-checklist.component').then(
                        (m) => m.TutorialGroupsChecklistComponent,
                    ),
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.pages.checklist.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/create-tutorial-groups-configuration',
                loadComponent: () =>
                    import(
                        'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component'
                    ).then((m) => m.CreateTutorialGroupsConfigurationComponent),
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.pages.createTutorialGroupsConfiguration.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/lti-configuration',
                loadComponent: () => import('app/course/manage/course-lti-configuration/course-lti-configuration.component').then((m) => m.CourseLtiConfigurationComponent),
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
                loadComponent: () =>
                    import('app/course/manage/course-lti-configuration/edit-course-lti-configuration.component').then((m) => m.EditCourseLtiConfigurationComponent),
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
                        loadComponent: () => import('app/orion/management/orion-course-management-exercises.component').then((m) => m.OrionCourseManagementExercisesComponent),
                        data: {
                            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.course.exercises',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'course-statistics',
                        loadComponent: () => import('./course-management-statistics.component').then((m) => m.CourseManagementStatisticsComponent),
                        data: {
                            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.courseStatistics.statistics',
                            breadcrumbLabelVariable: '',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'edit',
                        loadComponent: () => import('./course-update.component').then((m) => m.CourseUpdateComponent),
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.course.home.editLabel',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'groups/:courseGroup',
                        loadComponent: () => import('app/course/manage/course-group-membership/course-group-membership.component').then((m) => m.CourseGroupMembershipComponent),
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.userManagement.groups',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'ratings',
                        loadComponent: () => import('app/exercises/shared/rating/rating-list/rating-list.component').then((m) => m.RatingListComponent),
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.ratingList.pageTitle',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'competency-management',
                        loadComponent: () => import('app/course/competencies/competency-management/competency-management.component').then((m) => m.CompetencyManagementComponent),
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.competency.manage.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        // Create a new path without a component defined to prevent the CompetencyManagementComponent from being always rendered
                        path: 'competency-management',
                        data: {
                            pageTitle: 'artemisApp.competency.manage.title',
                        },
                        children: [
                            {
                                path: 'create',
                                loadComponent: () => import('app/course/competencies/create/create-competency.component').then((m) => m.CreateCompetencyComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.create.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: ':competencyId/edit',
                                loadComponent: () => import('app/course/competencies/edit/edit-competency.component').then((m) => m.EditCompetencyComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.editCompetency.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: 'import',
                                loadComponent: () => import('app/course/competencies/import/import-competencies.component').then((m) => m.ImportCompetenciesComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'import-standardized',
                                loadComponent: () =>
                                    import('app/course/competencies/import-standardized-competencies/course-import-standardized-competencies.component').then(
                                        (m) => m.CourseImportStandardizedCompetenciesComponent,
                                    ),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'generate',
                                loadComponent: () =>
                                    import('app/course/competencies/generate-competencies/generate-competencies.component').then((m) => m.GenerateCompetenciesComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.generate.title',
                                },
                                canActivate: [UserRouteAccessService, IrisGuard],
                                canDeactivate: [PendingChangesGuard],
                            },
                        ],
                    },
                    {
                        path: 'prerequisite-management',
                        redirectTo: 'competency-management',
                        pathMatch: 'full',
                    },
                    {
                        path: 'prerequisite-management',
                        data: {
                            pageTitle: 'artemisApp.prerequisite.manage.title',
                        },
                        children: [
                            {
                                path: 'create',
                                loadComponent: () => import('app/course/competencies/create/create-prerequisite.component').then((m) => m.CreatePrerequisiteComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.createPrerequisite.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: ':prerequisiteId/edit',
                                loadComponent: () => import('app/course/competencies/edit/edit-prerequisite.component').then((m) => m.EditPrerequisiteComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.editPrerequisite.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: 'import',
                                loadComponent: () => import('app/course/competencies/import/import-prerequisites.component').then((m) => m.ImportPrerequisitesComponent),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'import-standardized',
                                loadComponent: () =>
                                    import('app/course/competencies/import-standardized-competencies/course-import-standardized-prerequisites.component').then(
                                        (m) => m.CourseImportStandardizedPrerequisitesComponent,
                                    ),
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                        ],
                    },
                    {
                        path: 'learning-path-management',
                        loadComponent: () =>
                            import('app/course/learning-paths/pages/learning-path-instructor-page/learning-path-instructor-page.component').then(
                                (m) => m.LearningPathInstructorPageComponent,
                            ),
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.learningPath.manageLearningPaths.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'build-queue',
                        loadComponent: () => import('app/localci/build-queue/build-queue.component').then((m) => m.BuildQueueComponent),
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.buildQueue.title',
                        },
                        canActivate: [UserRouteAccessService, LocalCIGuard],
                    },
                    {
                        path: 'faqs',
                        children: [
                            {
                                path: '',
                                loadComponent: () => import('app/faq/faq.component').then((m) => m.FaqComponent),
                                resolve: {
                                    course: CourseManagementResolve,
                                },
                                data: {
                                    authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.faq.home.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                // Create a new path without a component defined to prevent the FAQ from being always rendered
                                path: '',
                                resolve: {
                                    course: CourseManagementResolve,
                                },
                                children: [
                                    {
                                        path: 'new',
                                        loadComponent: () => import('app/faq/faq-update.component').then((m) => m.FaqUpdateComponent),
                                        data: {
                                            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                                            pageTitle: 'global.generic.create',
                                        },
                                        canActivate: [UserRouteAccessService],
                                    },
                                    {
                                        path: ':faqId',
                                        resolve: {
                                            faq: FaqResolve,
                                        },
                                        children: [
                                            {
                                                path: 'edit',
                                                loadComponent: () => import('app/faq/faq-update.component').then((m) => m.FaqUpdateComponent),
                                                data: {
                                                    authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                                                    pageTitle: 'global.generic.edit',
                                                },
                                                canActivate: [UserRouteAccessService],
                                            },
                                        ],
                                    },
                                ],
                            },
                        ],
                    },
                ],
            },
        ],
    },
];
