import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { StudentExam } from 'app/entities/student-exam.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { addUserIndependentRepositoryUrl } from 'app/overview/participation-utils';
import { ExerciseType } from 'app/entities/exercise.model';

type EntityResponseType = HttpResponse<StudentExam>;
type EntityArrayResponseType = HttpResponse<StudentExam[]>;

@Injectable({ providedIn: 'root' })
export class StudentExamService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Find a student exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     */
    find(courseId: number, examId: number, studentExamId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.adjustRepositoryUrlsForProgrammingExercises(res)));
    }

    /**
     * Find all student exams for the given exam.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    findAllForExam(courseId: number, examId: number): Observable<EntityArrayResponseType> {
        return this.http.get<StudentExam[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams`, { observe: 'response' });
    }

    /**
     * Update the working time of the given student exam.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param studentExamId The id of the student exam to get.
     * @param workingTime The working time in seconds.
     */
    updateWorkingTime(courseId: number, examId: number, studentExamId: number, workingTime: number): Observable<EntityResponseType> {
        return this.http.patch<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}/working-time`, workingTime, { observe: 'response' });
    }

    toggleSubmittedState(courseId: number, examId: number, studentExamId: number, unsubmit: boolean): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/${studentExamId}/toggle-to-`;
        if (unsubmit) {
            return this.http.put<StudentExam>(url + `unsubmitted`, {}, { observe: 'response' });
        } else {
            return this.http.put<StudentExam>(url + `submitted`, {}, { observe: 'response' });
        }
    }

    protected adjustRepositoryUrlsForProgrammingExercises(res: EntityResponseType): EntityResponseType {
        if (res.body && res.body.exercises) {
            res.body.exercises!.forEach((ex) => {
                if (ex.type === ExerciseType.PROGRAMMING && ex.studentParticipations) {
                    ex.studentParticipations!.forEach((sp) => {
                        if (sp.type === ParticipationType.PROGRAMMING) {
                            addUserIndependentRepositoryUrl(sp);
                        }
                    });
                }
            });
        }
        return res;
    }
}
