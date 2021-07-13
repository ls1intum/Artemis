import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from './course-management.service';
import { CourseManagementComponent } from './course-management.component';
import { CourseDetailComponent } from './detail/course-detail.component';
import { CourseUpdateComponent } from './course-update.component';
import { CourseManagementExercisesComponent } from './course-management-exercises.component';
import { CourseGroupComponent } from 'app/course/manage/course-group.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';
import { CreateLearningGoalComponent } from 'app/course/learning-goals/create-learning-goal/create-learning-goal.component';
import { EditLearningGoalComponent } from 'app/course/learning-goals/edit-learning-goal/edit-learning-goal.component';
import { CourseParticipantScoresComponent } from 'app/course/course-participant-scores/course-participant-scores.component';
import { CourseManagementStatisticsComponent } from './course-management-statistics.component';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { PlagiarismCasesComponent } from 'app/course/plagiarism-cases/plagiarism-cases.component';

@Injectable({ providedIn: 'root' })
export class CourseResolve implements Resolve<Course> {
    constructor(private service: CourseManagementService) {}

    /**
     * Resolves the route by extracting the courseId and returns the course with that Id if it exists
     * and creates a new course otherwise
     * @param route - contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<Course> {
        if (route.params['courseId']) {
            return this.service.find(route.params['courseId']).pipe(
                filter((response: HttpResponse<Course>) => response.ok),
                map((course: HttpResponse<Course>) => course.body!),
            );
        }
        return of(new Course());
    }
}

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
            course: CourseResolve,
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
        path: ':courseId/plagiarism-cases',
        component: PlagiarismCasesComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.plagiarism.cases.plagiarism-cases',
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
    },
    {
        // Create a new path without a component defined to prevent resolver caching and the CourseDetailComponent from being always rendered
        path: ':courseId',
        resolve: {
            course: CourseResolve,
        },
        children: [
            {
                path: 'exercises',
                component: CourseManagementExercisesComponent,
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
                component: CourseGroupComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'userManagement.groups',
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
