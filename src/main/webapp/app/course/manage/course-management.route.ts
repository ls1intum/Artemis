import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementComponent } from './course-management.component';
import { CourseDetailComponent } from './detail/course-detail.component';
import { CourseUpdateComponent } from './course-update.component';
import { CourseManagementExercisesComponent } from './course-management-exercises.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';
import { CompetencyManagementComponent } from 'app/course/competencies/competency-management/competency-management.component';
import { CreateCompetencyComponent } from 'app/course/competencies/create/create-competency.component';
import { EditCompetencyComponent } from 'app/course/competencies/edit/edit-competency.component';
import { GenerateCompetenciesComponent } from 'app/course/competencies/generate-competencies/generate-competencies.component';
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
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { BuildQueueComponent } from 'app/localci/build-queue/build-queue.component';
import { ImportCompetenciesComponent } from 'app/course/competencies/import/import-competencies.component';
import { LocalCIGuard } from 'app/localci/localci-guard.service';
import { IrisGuard } from 'app/iris/iris-guard.service';
import { CourseImportStandardizedCompetenciesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-competencies.component';
import { ImportPrerequisitesComponent } from 'app/course/competencies/import/import-prerequisites.component';
import { CreatePrerequisiteComponent } from 'app/course/competencies/create/create-prerequisite.component';
import { EditPrerequisiteComponent } from 'app/course/competencies/edit/edit-prerequisite.component';
import { CourseImportStandardizedPrerequisitesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-prerequisites.component';
import { LearningPathInstructorPageComponent } from 'app/course/learning-paths/pages/learning-path-instructor-page/learning-path-instructor-page.component';
import { FaqComponent } from 'app/faq/faq.component';
import { FaqUpdateComponent } from 'app/faq/faq-update.component';
import { FaqResolve } from 'app/faq/faq.routes';

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
                                component: CreateCompetencyComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.create.title',
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
                            {
                                path: 'import',
                                component: ImportCompetenciesComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'import-standardized',
                                component: CourseImportStandardizedCompetenciesComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.competency.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'generate',
                                component: GenerateCompetenciesComponent,
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
                                component: CreatePrerequisiteComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.createPrerequisite.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: ':prerequisiteId/edit',
                                component: EditPrerequisiteComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.editPrerequisite.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: 'import',
                                component: ImportPrerequisitesComponent,
                                data: {
                                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.prerequisite.import.title',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [PendingChangesGuard],
                            },
                            {
                                path: 'import-standardized',
                                component: CourseImportStandardizedPrerequisitesComponent,
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
                        component: LearningPathInstructorPageComponent,
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.learningPath.manageLearningPaths.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'build-queue',
                        component: BuildQueueComponent,
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
                                component: FaqComponent,
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
                                        component: FaqUpdateComponent,
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
                                                component: FaqUpdateComponent,
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
