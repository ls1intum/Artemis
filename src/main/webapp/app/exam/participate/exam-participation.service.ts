import { Injectable, OnInit } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable, pipe } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { HttpClient } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService implements OnInit {
    private resourceUrl = SERVER_API_URL + 'api/courses';
    private studentExam: StudentExam;
    // TODO: add course id and exam id
    private localStorageExamKey = 'artemis_student_exam';

    constructor(private httpClient: HttpClient, private localStorageService: LocalStorageService) {}

    /**
     * Checks local storage for exam, if not found -> ask server for studentExam
     */
    ngOnInit() {
        // TODO: check if there is a local student exam
        const localStoredExam: StudentExam = this.localStorageService.retrieve(this.localStorageExamKey);

        if (localStoredExam) {
            this.studentExam = localStoredExam;
        } else {
            // download student exam from server on service init
            this.getStudentExamFromServer().subscribe((studentExam: StudentExam) => {
                this.studentExam = studentExam;
            });
        }
    }

    /**
     * Retrieves a {@link StudentExam} from server
     */
    private getStudentExamFromServer(): Observable<StudentExam> {
        // TODO: exchange with real call
        const mockedStudentExam: Partial<StudentExam> = {
            exam: undefined,
            exercises: [],
            id: 0,
            student: undefined,
        };
        return Observable.of(mockedStudentExam as StudentExam);
    }

    /**
     * Updates StudentExam on server
     * @param studentExam
     */
    updateStudentExam(studentExam: StudentExam): Observable<StudentExam> {
        this.localStorageService.store(this.localStorageExamKey, '');
        // TODO: exchange with real update
        return Observable.of(studentExam);
    }
}
