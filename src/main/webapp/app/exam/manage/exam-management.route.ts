import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupDetailComponent } from 'app/exam/manage/exercise-groups/exercise-group-detail.component';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';

@Injectable({ providedIn: 'root' })
export class ExamResolve implements Resolve<Exam> {
    constructor(private examManagementService: ExamManagementService) {}

    /**
     * Resolves the route by extracting the examId and returns the exam with that Id if it exists
     * or creates a new exam otherwise.
     * @param route Contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<Exam> {
        const id = route.params['examId'] ? route.params['examId'] : null;
        if (id) {
            return this.examManagementService.find(id).pipe(
                filter((response: HttpResponse<Exam>) => response.ok),
                map((course: HttpResponse<Exam>) => course.body!),
            );
        }
        return of(new Exam());
    }
}

@Injectable({ providedIn: 'root' })
export class ExerciseGroupResolve implements Resolve<ExerciseGroup> {
    constructor(private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Resolves the route by extracting the exerciseGroupId and returns the exercise group with that id if it exists
     * or creates a new exercise group otherwise.
     * @param route Contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<ExerciseGroup> {
        const id = route.params['exerciseGroupId'] ? route.params['exerciseGroupId'] : null;
        if (id) {
            return this.exerciseGroupService.find(id).pipe(
                filter((response: HttpResponse<ExerciseGroup>) => response.ok),
                map((course: HttpResponse<ExerciseGroup>) => course.body!),
            );
        }
        return of(new ExerciseGroup());
    }
}

@Injectable({ providedIn: 'root' })
export class StudentExamResolve implements Resolve<StudentExam> {
    constructor(private studentExamService: StudentExamService) {}

    /**
     * Resolves the route by extracting the studentExamId and returns the student exam with that id if it exists
     * or creates a new student exam otherwise.
     * @param route Contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<StudentExam> {
        const id = route.params['studentExamId'] ? route.params['studentExamId'] : null;
        if (id) {
            return this.studentExamService.find(id).pipe(
                filter((response: HttpResponse<StudentExam>) => response.ok),
                map((course: HttpResponse<StudentExam>) => course.body!),
            );
        }
        return of(new StudentExam());
    }
}

export const examManagementRoute: Routes = [
    {
        path: '',
        component: ExamManagementComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: ExamUpdateComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/edit',
        component: ExamUpdateComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/view',
        component: ExamDetailComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups',
        component: ExerciseGroupsComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups/new',
        component: ExerciseGroupUpdateComponent,
        resolve: {
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups/:exerciseGroupId/edit',
        component: ExerciseGroupUpdateComponent,
        resolve: {
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups/:exerciseGroupId/view',
        component: ExerciseGroupDetailComponent,
        resolve: {
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/students',
        component: ExamStudentsComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/studentExams',
        component: StudentExamsComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/studentExams/:studentExamId/view',
        component: StudentExamDetailComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const EXAM_MANAGEMENT_ROUTES = [...examManagementRoute];

export const examManagementState: Routes = [
    {
        path: '',
        children: EXAM_MANAGEMENT_ROUTES,
    },
];
