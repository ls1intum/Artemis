import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';
import { SERVER_API_URL } from 'app/app.constants';
import { Exam } from 'app/entities/exam.model';
import { createRequestOption } from 'app/shared/util/request-util';

type EntityResponseType = HttpResponse<Exam>;
type EntityArrayResponseType = HttpResponse<Exam[]>;

@Injectable({ providedIn: 'root' })
export class ExamManagementService {
    public resourceUrl = SERVER_API_URL + 'api/courses';
    // Mock Values
    private exams: Exam[];

    constructor(private router: Router, private http: HttpClient) {
        const exam1 = new Exam();
        exam1.id = 1;
        exam1.title = 'EIST Exam SS 2020';
        exam1.visibleDate = moment({ year: 2020, month: 6, day: 6, hour: 11, m: 45, s: 0, ms: 0 });
        exam1.startDate = moment({ year: 2020, month: 6, day: 6, hour: 12, m: 0, s: 0, ms: 0 });
        exam1.endDate = moment({ year: 2020, month: 6, day: 6, hour: 14, m: 0, s: 0, ms: 0 });
        exam1.registeredUsers = 1186;
        exam1.isAtLeastInstructor = true;
        exam1.isAtLeastTutor = true;
        const exam2 = new Exam();
        exam2.id = 2;
        exam2.title = 'EIST Repeat Exam SS 2020';
        exam2.visibleDate = moment({ year: 2020, month: 8, day: 12, hour: 10, m: 45, s: 0, ms: 0 });
        exam2.startDate = moment({ year: 2020, month: 8, day: 12, hour: 11, m: 0, s: 0, ms: 0 });
        exam2.endDate = moment({ year: 2020, month: 8, day: 12, hour: 13, m: 0, s: 0, ms: 0 });
        exam2.registeredUsers = 419;
        exam2.isAtLeastTutor = true;
        this.exams = [exam1, exam2];
    }

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
     */
    find(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http
            .get<Exam>(`${this.resourceUrl}/${courseId}/exams/${examId}`, { observe: 'response' })
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
    findAllForCourse(courseId: number): Exam[] {
        /*return this.http
            .get<Exam[]>(`${this.resourceUrl}/${courseId}/exams`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => ExamManagementService.convertDateArrayFromServer(res)));

         */
        return this.exams;
    }

    /**
     * Delete an exam on the server using a DELETE request.
     * @param courseId The course id.
     * @param examId The id of the exam to delete.
     */
    delete(courseId: number, examId: number) {
        // return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}`, { observe: 'response' });
        this.exams = this.exams.filter((t) => t.id !== examId);
        console.log('Deleted from Server, length:'.concat(String(this.exams.length)));
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
