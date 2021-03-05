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
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { TextExerciseResolver } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { FileUploadExerciseResolve } from 'app/exercises/file-upload/manage/file-upload-exercise-management.route';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseResolve } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { ModelingExerciseResolver } from 'app/exercises/modeling/manage/modeling-exercise.route';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { AssessmentDashboardComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.component';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';

@Injectable({ providedIn: 'root' })
export class ExamResolve implements Resolve<Exam> {
    constructor(private examManagementService: ExamManagementService) {}

    /**
     * Resolves the route by extracting the examId and returns the exam with that Id if it exists
     * or creates a new exam otherwise.
     * @param route Contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<Exam> {
        const courseId = route.params['courseId'] ? route.params['courseId'] : undefined;
        const examId = route.params['examId'] ? route.params['examId'] : undefined;
        const withStudents = route.data['requestOptions'] ? route.data['requestOptions'].withStudents : false;
        if (courseId && examId) {
            return this.examManagementService.find(courseId, examId, withStudents).pipe(
                filter((response: HttpResponse<Exam>) => response.ok),
                map((exam: HttpResponse<Exam>) => exam.body!),
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
        const courseId = route.params['courseId'] || undefined;
        const examId = route.params['examId'] || undefined;
        const exerciseGroupId = route.params['exerciseGroupId'] || undefined;
        if (courseId && examId && exerciseGroupId) {
            return this.exerciseGroupService.find(courseId, examId, exerciseGroupId).pipe(
                filter((response: HttpResponse<ExerciseGroup>) => response.ok),
                map((exerciseGroup: HttpResponse<ExerciseGroup>) => exerciseGroup.body!),
            );
        }
        return of({ isMandatory: true } as ExerciseGroup);
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
        const courseId = route.params['courseId'] || undefined;
        const examId = route.params['examId'] || undefined;
        const studentExamId = route.params['studentExamId'] ? route.params['studentExamId'] : route.params['testRunId'];
        if (courseId && examId && studentExamId) {
            return this.studentExamService.find(courseId, examId, studentExamId).pipe(
                filter((response: HttpResponse<StudentExam>) => response.ok),
                map((studentExam: HttpResponse<StudentExam>) => studentExam.body!),
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
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
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
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
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
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId',
        component: ExamDetailComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                withStudents: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups',
        component: ExerciseGroupsComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/new',
        component: ExerciseGroupUpdateComponent,
        resolve: {
            exam: ExamResolve,
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:exerciseGroupId/edit',
        component: ExerciseGroupUpdateComponent,
        resolve: {
            exam: ExamResolve,
            exerciseGroup: ExerciseGroupResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/students',
        component: ExamStudentsComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
            requestOptions: {
                withStudents: true,
            },
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams',
        component: StudentExamsComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs',
        component: TestRunManagementComponent,
        resolve: {
            exam: ExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/assess',
        component: AssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:studentExamId',
        component: StudentExamDetailComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId',
        component: StudentExamDetailComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/student-exams/:studentExamId/summary',
        component: StudentExamSummaryComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/test-runs/:testRunId/conduction',
        component: ExamParticipationComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':examId/test-runs/:studentExamId/summary',
        component: StudentExamSummaryComponent,
        resolve: {
            studentExam: StudentExamResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Modeling Exercise
    {
        path: ':examId/exercise-groups/:groupId/modeling-exercises/new',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Modeling Exercise
    {
        path: ':examId/exercise-groups/:groupId/modeling-exercises/import/:exerciseId',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Modeling Exercise
    {
        path: ':examId/exercise-groups/:groupId/modeling-exercises/:exerciseId/edit',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Text Exercise
    {
        path: ':examId/exercise-groups/:groupId/text-exercises/new',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import Text Exercise
    {
        path: ':examId/exercise-groups/:groupId/text-exercises/import/:exerciseId',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Text Exercise
    {
        path: ':examId/exercise-groups/:groupId/text-exercises/:exerciseId/edit',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create File Upload Exercise
    {
        path: ':examId/exercise-groups/:groupId/file-upload-exercises/new',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit File Upload Exercise
    {
        path: ':examId/exercise-groups/:groupId/file-upload-exercises/:exerciseId/edit',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Quiz Exercise
    {
        path: ':examId/exercise-groups/:groupId/quiz-exercises/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Quiz Exercise
    {
        path: ':examId/exercise-groups/:groupId/quiz-exercises/:exerciseId/edit',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Create Programming Exercise
    {
        path: ':examId/exercise-groups/:groupId/programming-exercises/new',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // Import programming exercise
    {
        path: ':examId/exercise-groups/:groupId/programming-exercises/import/:id',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    // Edit Programming Exercise
    {
        path: ':examId/exercise-groups/:groupId/programming-exercises/:id/edit',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/assessment-dashboard',
        component: AssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examManagement.assessmentDashboard',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:groupId/assessment-dashboard/:exerciseId',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exercise-groups/:groupId/test-assessment-dashboard/:exerciseId',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
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
