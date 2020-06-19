import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';
import { SERVER_API_URL } from 'app/app.constants';
import { Exam } from 'app/entities/exam.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { StudentDTO } from 'app/entities/student-dto.model';
import { StudentExam } from 'app/entities/student-exam.model';

type EntityResponseType = HttpResponse<Exam>;
type EntityArrayResponseType = HttpResponse<Exam[]>;

@Injectable({ providedIn: 'root' })
export class ExamManagementService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Create an exam on the server using a POST request.
     * @param courseId The course id.
     * @param exam The exam to create.
     */
    create(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertDateFromClient(exam);
        return this.http
            .post<Exam>(`${this.resourceUrl}/${courseId}/exams`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
    }

    /**
     * Update an exam on the server using a PUT request.
     * @param courseId The course id.
     * @param exam The exam to update.
     */
    update(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertDateFromClient(exam);
        return this.http
            .put<Exam>(`${this.resourceUrl}/${courseId}/exams`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
    }

    /**
     * Find an exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The id of the exam to get.
     * @param withStudents Boolean flag whether to fetch all students registered for the exam
     */
    find(courseId: number, examId: number, withStudents = false): Observable<EntityResponseType> {
        const options = createRequestOption({ withStudents });
        return this.http
            .get<Exam>(`${this.resourceUrl}/${courseId}/exams/${examId}`, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
    }

    /**
     * Query exams of the given course via get request.
     * @param courseId The course id.
     * @param req The query request options.
     */
    query(courseId: number, req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Exam[]>(`${this.resourceUrl}/${courseId}/exams`, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => ExamManagementService.convertDateArrayFromServer(res)));
    }

    /**
     * Find all exams for the given course.
     * @param courseId The course id.
     */
    findAllExamsForCourse(courseId: number): Observable<HttpResponse<Exam[]>> {
        return this.http
            .get<Exam[]>(`${this.resourceUrl}/${courseId}/exams`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => ExamManagementService.convertDateArrayFromServer(res)));
    }

    /**
     * Delete an exam on the server using a DELETE request.
     * @param courseId The course id.
     * @param examId The id of the exam to delete.
     */
    delete(courseId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}`, { observe: 'response' });
    }

    /**
     * Add a student to the registered users for an exam
     * @param courseId The course id.
     * @param examId The id of the exam to which to add the student
     * @param studentLogin Login of the student
     */
    addStudentToExam(courseId: number, examId: number, studentLogin: string): Observable<HttpResponse<any>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/students/${studentLogin}`, { observe: 'response' });
    }

    /**
     * Add students to the registered users for an exam
     * @param courseId The course id.
     * @param examId The id of the exam to which to add the student
     * @param studentDtos Student DTOs of student to add to the exam
     * @return studentDtos of students that were not found in the system
     */
    addStudentsToExam(courseId: number, examId: number, studentDtos: StudentDTO[]): Observable<HttpResponse<StudentDTO[]>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/students`, studentDtos, { observe: 'response' });
    }

    /**
     * Remove a student to the registered users for an exam
     * @param courseId The course id.
     * @param examId The id of the exam from which to remove the student
     * @param studentLogin Login of the student
     */
    removeStudentFromExam(courseId: number, examId: number, studentLogin: string): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/students/${studentLogin}`, { observe: 'response' });
    }

    /**
     * Generate all student exams for all registered students of the exam.
     * @param courseId
     * @param examId
     * @returns a list with the generate student exams
     */
    generateStudentExams(courseId: number, examId: number): Observable<HttpResponse<StudentExam[]>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/generate-student-exams`, { observe: 'response' });
    }

    private static convertDateFromClient(exam: Exam): Exam {
        return Object.assign({}, exam, {
            startDate: exam.startDate && moment(exam.startDate).isValid() ? exam.startDate.toJSON() : null,
            endDate: exam.endDate && moment(exam.endDate).isValid() ? exam.endDate.toJSON() : null,
            visibleDate: exam.visibleDate && moment(exam.visibleDate).isValid() ? exam.visibleDate.toJSON() : null,
        });
    }

    private static convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.startDate = res.body.startDate ? moment(res.body.startDate) : null;
            res.body.endDate = res.body.endDate ? moment(res.body.endDate) : null;
            res.body.visibleDate = res.body.visibleDate ? moment(res.body.visibleDate) : null;
        }
        return res;
    }

    private static convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((exam: Exam) => {
                exam.startDate = exam.startDate ? moment(exam.startDate) : null;
                exam.endDate = exam.endDate ? moment(exam.endDate) : null;
                exam.visibleDate = exam.visibleDate ? moment(exam.visibleDate) : null;
            });
        }
        return res;
    }
}
