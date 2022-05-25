import { Injectable } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    examObservables: Map<number, BehaviorSubject<Exam | undefined>> = new Map<number, BehaviorSubject<Exam>>();

    constructor() {}

    /**
     * Notify all exam subscribers with the newest exam provided.
     * @param exam received or updated exam
     */
    public notifyExamSubscribers = (exam: Exam) => {
        console.log(exam);
        const examObservable = this.examObservables.get(exam.id!);
        if (!examObservable) {
            this.examObservables.set(exam.id!, new BehaviorSubject(exam));
        } else {
            examObservable.next(exam);
        }
    };

    /**
     * Get exam as observable
     * @param examId exam to observe
     */
    public getExamBehaviorSubject(examId: number): BehaviorSubject<Exam | undefined> | undefined {
        return this.examObservables.get(examId);
    }
}
