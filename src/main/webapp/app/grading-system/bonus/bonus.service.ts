import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GradeDTO, GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { map } from 'rxjs/operators';
import { Bonus } from 'app/entities/bonus.model';

export type EntityResponseType = HttpResponse<Bonus>;

@Injectable({ providedIn: 'root' })
export class BonusService {
    public resourceUrl = SERVER_API_URL + 'api/';

    constructor(private http: HttpClient) {}

    // /**
    //  * Store a new bonus source for course on the server
    //  *
    //  * @param targetExamId the exam for which the bonus source will be created
    //  * @param bonus the bonus source to be created
    //  */
    // createBonusForCourse(targetExamId: number, bonus: Bonus): Observable<EntityResponseType> {
    //     return this.http.post<Bonus>(`${this.resourceUrl}/exams/${targetExamId}/bonus-sources`, bonus, { observe: 'response' });
    // }

    // /**
    //  * Update a bonus source for course on the server
    //  *
    //  * @param targetExamId the course for which the bonus source will be updated
    //  * @param bonus the bonus source to be updated
    //  */
    // updateBonusForCourse(targetExamId: number, bonus: Bonus): Observable<EntityResponseType> {
    //     return this.http.put<Bonus>(`${this.resourceUrl}/${targetExamId}/bonus-sources`, bonus, { observe: 'response' });
    // }
    //
    // /**
    //  * Retrieves the bonus source for course
    //  *
    //  * @param targetExamId the course for which the bonus source will be retrieved
    //  */
    // findBonusForCourse(targetExamId: number): Observable<EntityResponseType> {
    //     return this.http.get<Bonus>(`${this.resourceUrl}/${targetExamId}/bonus-sources`, { observe: 'response' });
    // }

    /**
     * Deletes the bonus source for course
     *
     * @param targetExamId the course for which the bonus source will be deleted
     */
    deleteBonusForCourse(targetExamId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${targetExamId}/bonus-sources`, { observe: 'response' });
    }

    /**
     * Store a new bonus source for exam on the server
     *
     * @param targetExamId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be created
     * @param bonus the bonus source to be created
     */
    createBonusForTargetExam(targetExamId: number, examId: number, bonus: Bonus): Observable<EntityResponseType> {
        return this.http.post<Bonus>(`${this.resourceUrl}/${targetExamId}/exams/${examId}/bonus-sources`, bonus, { observe: 'response' });
    }

    /**
     * Update a bonus source for exam on the server
     *
     * @param bonus the bonus source to be updated
     */
    updateBonus(bonus: Bonus): Observable<EntityResponseType> {
        return this.http.put<Bonus>(`${this.resourceUrl}/bonus-sources`, bonus, { observe: 'response' });
    }

    /**
     * Retrieves the bonus source for exam
     *
     * @param targetExamId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be retrieved
     */
    findBonusWithTargetExam(targetExamId: number, examId: number): Observable<EntityResponseType> {
        return this.http.get<Bonus>(`${this.resourceUrl}/${targetExamId}/exams/${examId}/bonus-sources`, { observe: 'response' });
    }

    /**
     * Deletes the bonus source for exam
     *
     * @param targetExamId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be deleted
     */
    deleteBonusForExam(targetExamId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${targetExamId}/exams/${examId}/bonus-sources`, { observe: 'response' });
    }

    // /**
    //  * Finds all grade steps for a course or an exam
    //  *
    //  * @param targetExamId the course for which the grade steps are queried
    //  * @param examId if present the grade steps for this exam are queried instead
    //  */
    // findGradeSteps(targetExamId: number, examId?: number): Observable<GradeStepsDTO | undefined> {
    //     let gradeStepsObservable: Observable<HttpResponse<GradeStepsDTO>>;
    //     if (examId != undefined) {
    //         gradeStepsObservable = this.findGradeStepsForExam(targetExamId, examId);
    //     } else {
    //         gradeStepsObservable = this.findGradeStepsForCourse(targetExamId);
    //     }
    //     return gradeStepsObservable.pipe(
    //         map((gradeStepsDTO) => {
    //             if (gradeStepsDTO && gradeStepsDTO.body) {
    //                 return gradeStepsDTO.body;
    //             }
    //         }),
    //     );
    // }
}
