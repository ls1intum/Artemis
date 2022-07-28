import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GradeDTO, GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { map } from 'rxjs/operators';
import { Bonus, BonusExample } from 'app/entities/bonus.model';
import { GradingScale } from 'app/entities/grading-scale.model';

export type EntityResponseType = HttpResponse<Bonus>;

@Injectable({ providedIn: 'root' })
export class BonusService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    // /**
    //  * Store a new bonus source for course on the server
    //  *
    //  * @param targetExamId the exam for which the bonus source will be created
    //  * @param bonus the bonus source to be created
    //  */
    // createBonusForCourse(targetExamId: number, bonus: Bonus): Observable<EntityResponseType> {
    //     return this.http.post<Bonus>(`${this.resourceUrl}/exams/${targetExamId}/bonus`, bonus, { observe: 'response' });
    // }

    // /**
    //  * Update a bonus source for course on the server
    //  *
    //  * @param targetExamId the course for which the bonus source will be updated
    //  * @param bonus the bonus source to be updated
    //  */
    // updateBonusForCourse(targetExamId: number, bonus: Bonus): Observable<EntityResponseType> {
    //     return this.http.put<Bonus>(`${this.resourceUrl}/${targetExamId}/bonus`, bonus, { observe: 'response' });
    // }
    //
    // /**
    //  * Retrieves the bonus source for course
    //  *
    //  * @param targetExamId the course for which the bonus source will be retrieved
    //  */
    // findBonusForCourse(targetExamId: number): Observable<EntityResponseType> {
    //     return this.http.get<Bonus>(`${this.resourceUrl}/${targetExamId}/bonus`, { observe: 'response' });
    // }

    /**
     * Deletes the bonus source for course
     *
     * @param bonusId the id of the bonus which will be deleted
     */
    deleteBonus(bonusId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/bonus/${bonusId}`, { observe: 'response' });
    }

    /**
     * Store a new bonus source for exam on the server
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be created
     * @param bonus the bonus source to be created
     */
    createBonusForExam(courseId: number, examId: number, bonus: Bonus): Observable<EntityResponseType> {
        return this.http.post<Bonus>(`${this.resourceUrl}/courses/${courseId}/exams/${examId}/bonus`, bonus, { observe: 'response' });
    }

    /**
     * Update a bonus source for exam on the server
     *
     * @param bonus the bonus source to be updated
     */
    updateBonus(bonus: Bonus): Observable<EntityResponseType> {
        return this.http.put<Bonus>(`${this.resourceUrl}/bonus`, bonus, { observe: 'response' });
    }

    /**
     * Retrieves the bonus source for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be retrieved
     */
    findBonusWithTargetExam(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http.get<Bonus>(`${this.resourceUrl}/courses/${courseId}/exams/${examId}/bonus`, { observe: 'response' });
    }

    /**
     * Deletes the bonus source for exam
     *
     * @param targetExamId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be deleted
     */
    deleteBonusForExam(targetExamId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/courses/${targetExamId}/exams/${examId}/bonus`, { observe: 'response' });
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

    /**
     * TODO: Ata
     * @param target gradeSteps are assumed to be sorted
     * @param source gradeSteps are assumed to be sorted
     */
    generateExampleExamAndBonusPoints(target: GradeStepsDTO, source: GradingScale) {
        const examples: BonusExample[] = [];
        examples.push(new BonusExample(0, undefined));

        let targetGradeStepIndex = target.gradeSteps.findIndex((gs) => gs.isPassingGrade);
        let sourceGradeStepIndex = source.gradeSteps.length - 1;

        for (let i = 0; i < 3; i++) {
            const targetGradeStep = target.gradeSteps[targetGradeStepIndex];
            const examStudentPoints = this.getIncludedBoundaryPoints(targetGradeStep) ?? targetGradeStep.lowerBoundPoints;

            const sourceGradeStep = source.gradeSteps[sourceGradeStepIndex];
            const bonusStudentPoints = this.getIncludedBoundaryPoints(sourceGradeStep) ?? sourceGradeStep.lowerBoundPoints;

            examples.push(new BonusExample(examStudentPoints!, bonusStudentPoints!));

            sourceGradeStepIndex = this.modulo(sourceGradeStepIndex - 1, source.gradeSteps.length);
            targetGradeStepIndex = this.modulo(targetGradeStepIndex + 1, target.gradeSteps.length);
        }

        targetGradeStepIndex = target.gradeSteps.length - 1;
        const lastTargetGradeStep = target.gradeSteps[targetGradeStepIndex];
        const lastExamStudentPoints = this.getIncludedBoundaryPoints(lastTargetGradeStep) ?? lastTargetGradeStep.lowerBoundPoints;

        sourceGradeStepIndex = source.gradeSteps.length - 1;
        const lastSourceGradeStep = source.gradeSteps[sourceGradeStepIndex];
        const lastBonusStudentPoints = this.getIncludedBoundaryPoints(lastSourceGradeStep) ?? lastSourceGradeStep.lowerBoundPoints;

        examples.push(new BonusExample(lastExamStudentPoints!, lastBonusStudentPoints!));

        return examples;
    }

    getIncludedBoundaryPoints(gradeStep: GradeStep) {
        if (gradeStep.lowerBoundInclusive) {
            return gradeStep.lowerBoundPoints;
        }
        if (gradeStep.upperBoundInclusive) {
            return gradeStep.upperBoundPoints;
        }
        return undefined;
    }

    /**
     * As opposed to % operator, this method always returns a non-negative number.
     * @param n as in n mod m
     * @param m as in n mod m
     * @private
     */
    private modulo(n: number, m: number) {
        return ((n % m) + m) % m;
    }
}
