import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_ADMIN, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { TutorialGroupManagementResolve } from 'app/tutorialgroup/manage/service/tutorial-group-management-resolve.service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { LocalCIGuard } from 'app/buildagent/shared/localci-guard.service';
import { IrisGuard } from 'app/iris/shared/iris-guard.service';
import { FaqResolve } from 'app/communication/faq/faq-resolve.service';
import { CourseManagementResolve } from 'app/core/course/manage/services/course-management-resolve.service';
import { ExerciseAssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component';

export const courseManagementRoutes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/core/course/manage/course-management/course-management.component').then((m) => m.CourseManagementComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        loadComponent: () => import('./update/course-update.component').then((m) => m.CourseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_ADMIN,
            pageTitle: 'global.generic.create',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: '',
        loadComponent: () => import('app/core/course/manage/course-management-container/course-management-container.component').then((m) => m.CourseManagementContainerComponent),
        children: [
            {
                path: ':courseId',
                loadComponent: () => import('./detail/course-detail.component').then((m) => m.CourseDetailComponent),
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.course.overview',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/grading-system',
                loadComponent: () => import('app/assessment/manage/grading-system/grading-system.component').then((m) => m.GradingSystemComponent),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.course.gradingSystem',
                },
                canActivate: [UserRouteAccessService],
                loadChildren: () => import('app/assessment/manage/grading-system/grading-system.route').then((m) => m.gradingSystemRoutes),
            },
            {
                path: ':courseId/iris-settings',
                loadComponent: () =>
                    import('app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component').then((m) => m.IrisCourseSettingsUpdateComponent),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.iris.settings.title.course',
                },
                canActivate: [UserRouteAccessService, IrisGuard],
                canDeactivate: [PendingChangesGuard],
            },
            {
                path: ':courseId/lectures',
                loadChildren: () => import('app/lecture/manage/lecture.route').then((m) => m.lectureRoutes),
            },
            {
                path: ':courseId/tutorial-groups',
                resolve: {
                    course: TutorialGroupManagementResolve,
                },
                loadChildren: () => import('app/tutorialgroup/manage/tutorial-groups-management.route').then((m) => m.tutorialGroupManagementRoutes),
            },
            {
                path: ':courseId/assessment-dashboard/:exerciseId',
                loadComponent: () => ExerciseAssessmentDashboardComponent,
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/assessment-dashboard',
                loadComponent: () => import('app/assessment/shared/assessment-dashboard/assessment-dashboard.component').then((m) => m.AssessmentDashboardComponent),
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.assessmentDashboard.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/scores',
                loadComponent: () => import('app/core/course/manage/course-scores/course-scores.component').then((m) => m.CourseScoresComponent),
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: IS_AT_LEAST_TUTOR,
                    pageTitle: 'artemisApp.instructorDashboard.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/plagiarism-cases',
                loadChildren: () => import('app/plagiarism/manage/instructor-view/plagiarism-instructor-view.route').then((m) => m.plagiarismInstructorRoutes),
            },
            {
                path: ':courseId/exams/:examId/plagiarism-cases',
                loadChildren: () => import('app/plagiarism/manage/instructor-view/plagiarism-instructor-view.route').then((m) => m.plagiarismInstructorRoutes),
            },
            {
                path: ':courseId/exams',
                loadChildren: () => import('app/exam/manage/exam-management.route').then((m) => m.examManagementRoutes),
            },
            {
                path: ':courseId/tutorial-groups-checklist',
                loadComponent: () =>
                    import('app/tutorialgroup/manage/tutorial-groups-checklist/tutorial-groups-checklist.component').then((m) => m.TutorialGroupsChecklistComponent),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.pages.checklist.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/create-tutorial-groups-configuration',
                loadComponent: () =>
                    import('app/tutorialgroup/manage/tutorial-groups-configuration/crud/create-tutorial-groups-configuration/create-tutorial-groups-configuration.component').then(
                        (m) => m.CreateTutorialGroupsConfigurationComponent,
                    ),
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.pages.createTutorialGroupsConfiguration.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/lti-configuration',
                loadComponent: () => import('app/core/course/manage/course-lti-configuration/course-lti-configuration.component').then((m) => m.CourseLtiConfigurationComponent),
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
                    pageTitle: 'artemisApp.lti.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':courseId/lti-configuration/edit',
                loadComponent: () =>
                    import('app/core/course/manage/course-lti-configuration/edit-course-lti-configuration.component').then((m) => m.EditCourseLtiConfigurationComponent),
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: IS_AT_LEAST_INSTRUCTOR,
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
                        loadComponent: () => import('app/core/course/manage/exercises/course-management-exercises.component').then((m) => m.CourseManagementExercisesComponent),
                        data: {
                            authorities: IS_AT_LEAST_TUTOR,
                            pageTitle: 'artemisApp.course.exercises',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'communication',
                        loadComponent: () => import('app/communication/shared/course-conversations/course-conversations.component').then((m) => m.CourseConversationsComponent),
                        data: {
                            authorities: IS_AT_LEAST_TUTOR,
                            pageTitle: 'overview.communication',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'course-statistics',
                        loadComponent: () => import('./statistics/course-management-statistics.component').then((m) => m.CourseManagementStatisticsComponent),
                        data: {
                            authorities: IS_AT_LEAST_TUTOR,
                            pageTitle: 'artemisApp.courseStatistics.statistics',
                            breadcrumbLabelVariable: '',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'settings',
                        loadComponent: () => import('./update/course-update.component').then((m) => m.CourseUpdateComponent),
                        data: {
                            authorities: IS_AT_LEAST_INSTRUCTOR,
                            pageTitle: 'overview.settings',
                        },
                        resolve: {
                            course: CourseManagementResolve,
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'groups/:courseGroup',
                        loadComponent: () =>
                            import('app/core/course/manage/course-group-membership/course-group-membership.component').then((m) => m.CourseGroupMembershipComponent),
                        data: {
                            authorities: IS_AT_LEAST_INSTRUCTOR,
                            pageTitle: 'artemisApp.userManagement.groups',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'ratings',
                        loadComponent: () => import('app/assessment/manage/rating/rating-list/rating-list.component').then((m) => m.RatingListComponent),
                        data: {
                            authorities: IS_AT_LEAST_INSTRUCTOR,
                            pageTitle: 'artemisApp.ratingList.pageTitle',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'competency-management',
                        loadComponent: () => import('app/atlas/manage/competency-management/competency-management.component').then((m) => m.CompetencyManagementComponent),
                        data: {
                            authorities: IS_AT_LEAST_INSTRUCTOR,
                            pageTitle: 'artemisApp.competency.manage.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/assessment/manage/list-of-complaints/list-of-complaints.route').then((m) => m.listOfComplaintsRoute),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/assessment/manage/assessment-locks/assessment-locks.route').then((m) => m.assessmentLocksRoute),
                    },
                    // we have to define the redirects here. When we define them in the child routes, the redirect doesn't work
                    {
                        path: 'text-exercises',
                        redirectTo: 'exercises',
                    },
                    {
                        path: 'modeling-exercises',
                        redirectTo: 'exercises',
                    },
                    {
                        path: 'file-upload-exercises',
                        redirectTo: 'exercises',
                    },
                    {
                        path: 'quiz-exercises',
                        redirectTo: 'exercises',
                    },
                    {
                        path: 'programming-exercises',
                        redirectTo: 'exercises',
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/text/manage/text-exercise/text-exercise.route').then((m) => m.textExerciseRoute),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/programming/manage/programming-exercise-management.route').then((m) => m.routes),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/quiz/manage/quiz-management.route').then((m) => m.quizManagementRoute),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/fileupload/manage/file-upload-exercise-management.route').then((m) => m.routes),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/modeling/manage/modeling-exercise.route').then((m) => m.routes),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/exercise/exercise-scores/exercise-scores.route').then((m) => m.routes),
                    },
                    {
                        path: '',
                        loadChildren: () => import('app/exercise/participation/participation.route').then((m) => m.routes),
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
                                loadComponent: () => import('app/atlas/manage/create/create-competency.component').then((m) => m.CreateCompetencyComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.competency.create.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: ':competencyId/edit',
                                loadComponent: () => import('app/atlas/manage/edit/edit-competency.component').then((m) => m.EditCompetencyComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.competency.edit.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: 'import',
                                loadComponent: () => import('app/atlas/manage/import/import-competencies.component').then((m) => m.ImportCompetenciesComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.competency.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'import-standardized',
                                loadComponent: () =>
                                    import('app/atlas/manage/import-standardized-competencies/course-import-standardized-competencies.component').then(
                                        (m) => m.CourseImportStandardizedCompetenciesComponent,
                                    ),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.competency.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'generate',
                                loadComponent: () => import('app/atlas/manage/generate-competencies/generate-competencies.component').then((m) => m.GenerateCompetenciesComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
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
                                loadComponent: () => import('app/atlas/manage/create/create-prerequisite.component').then((m) => m.CreatePrerequisiteComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.prerequisite.createPrerequisite.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: ':prerequisiteId/edit',
                                loadComponent: () => import('app/atlas/manage/edit/edit-prerequisite.component').then((m) => m.EditPrerequisiteComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.prerequisite.editPrerequisite.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: 'import',
                                loadComponent: () => import('app/atlas/manage/import/import-prerequisites.component').then((m) => m.ImportPrerequisitesComponent),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
                                    pageTitle: 'artemisApp.prerequisite.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'import-standardized',
                                loadComponent: () =>
                                    import('app/atlas/manage/import-standardized-competencies/course-import-standardized-prerequisites.component').then(
                                        (m) => m.CourseImportStandardizedPrerequisitesComponent,
                                    ),
                                data: {
                                    authorities: IS_AT_LEAST_INSTRUCTOR,
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
                            import('app/atlas/manage/learning-path-instructor-page/learning-path-instructor-page.component').then((m) => m.LearningPathInstructorPageComponent),
                        data: {
                            authorities: IS_AT_LEAST_INSTRUCTOR,
                            pageTitle: 'artemisApp.learningPath.manageLearningPaths.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'build-queue',
                        loadComponent: () => import('app/buildagent/build-queue/build-overview.component').then((m) => m.BuildOverviewComponent),
                        data: {
                            authorities: IS_AT_LEAST_INSTRUCTOR,
                            pageTitle: 'artemisApp.buildQueue.title',
                        },
                        canActivate: [UserRouteAccessService, LocalCIGuard],
                    },
                    {
                        path: 'faqs',
                        children: [
                            {
                                path: '',
                                loadComponent: () => import('app/communication/faq/faq.component').then((m) => m.FaqComponent),
                                resolve: {
                                    course: CourseManagementResolve,
                                },
                                data: {
                                    authorities: IS_AT_LEAST_TUTOR,
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
                                        loadComponent: () => import('app/communication/faq/faq-update.component').then((m) => m.FaqUpdateComponent),
                                        data: {
                                            authorities: IS_AT_LEAST_TUTOR,
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
                                                loadComponent: () => import('app/communication/faq/faq-update.component').then((m) => m.FaqUpdateComponent),
                                                data: {
                                                    authorities: IS_AT_LEAST_TUTOR,
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
