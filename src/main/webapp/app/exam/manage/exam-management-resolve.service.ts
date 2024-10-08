import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Exam } from 'app/entities/exam/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, filter, map, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class CourseResolve implements Resolve<Course | null> {
    private courseManagementService = inject(CourseManagementService);

    resolve(route: ActivatedRouteSnapshot): Observable<Course | null> {
        const courseId = route.params['courseId'];

        if (courseId) {
            return this.courseManagementService.find(courseId).pipe(
                map((response) => response.body),
                catchError(() => of(null)),
            );
        }

        return of(null);
    }
}

@Injectable({ providedIn: 'root' })
export class ExamResolve implements Resolve<Exam> {
    private examManagementService = inject(ExamManagementService);

    /**
     * Resolves the route by extracting the examId and returns the exam with that id if it exists
     * or creates a new exam otherwise.
     * @param route Contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<Exam> {
        const courseId = route.params['courseId'] ? route.params['courseId'] : undefined;
        const examId = route.params['examId'] ? route.params['examId'] : undefined;
        const withStudents = route.data['requestOptions'] ? route.data['requestOptions'].withStudents : false;
        const withExerciseGroups = route.data['requestOptions'] ? route.data['requestOptions'].withExerciseGroups : false;
        const isImport = route.data['requestOptions']?.forImport ?? false;
        if (isImport && examId) {
            // When importing an exam, the courseId cannot be used, as the exam.course.id may deviate from the target course id
            // Additionally, the exercises are needed to select the exercises, that should be imported alongside the exam
            return this.examManagementService.findWithExercisesAndWithoutCourseId(examId).pipe(
                filter((response: HttpResponse<Exam>) => response.ok),
                map((response: HttpResponse<Exam>) => response.body!),
            );
        } else if (courseId && examId) {
            return this.examManagementService.find(courseId, examId, withStudents, withExerciseGroups).pipe(
                filter((response: HttpResponse<Exam>) => response.ok),
                map((response: HttpResponse<Exam>) => response.body!),
            );
        }
        return of(new Exam());
    }
}

@Injectable({ providedIn: 'root' })
export class ExerciseGroupResolve implements Resolve<ExerciseGroup> {
    private exerciseGroupService = inject(ExerciseGroupService);

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
export class StudentExamResolve implements Resolve<StudentExamWithGradeDTO> {
    private studentExamService = inject(StudentExamService);

    /**
     * Resolves the route by extracting the studentExamId and returns the student exam with that id if it exists
     * or creates a new student exam otherwise.
     * @param route Contains the information about the route to be resolved
     */
    resolve(route: ActivatedRouteSnapshot): Observable<StudentExamWithGradeDTO> {
        const courseId = route.params['courseId'] || undefined;
        const examId = route.params['examId'] || undefined;
        const studentExamId = route.params['studentExamId'] ? route.params['studentExamId'] : route.params['testRunId'];
        if (courseId && examId && studentExamId) {
            return this.studentExamService.find(courseId, examId, studentExamId).pipe(
                filter((response) => response.ok),
                map((response) => response.body!),
            );
        }
        return of(new StudentExamWithGradeDTO());
    }
}
