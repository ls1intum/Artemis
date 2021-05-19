import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { EntityResponseType, ResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';
import { map } from 'rxjs/operators';
import { AuxiliaryRepository } from 'app/entities/auxiliary-repository-model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class AuxiliaryRepositoryService {
    constructor(private http: HttpClient, private resultService: ResultService) {}

    /**
     * Persist a new result for the provided exercise and student (a participation and an empty submission will also be created if they do not exist yet)
     * @param { Exercise } exercise - Exercise for which a new result is created
     * @param { User } student - Student for whom a result is created
     * @param { Result } result - Result that is added
     */
    savexy(exercise: Exercise, student: User, result: Result): Observable<EntityResponseType> {
        return this.http
            .post<Result>(`${SERVER_API_URL}api/exercises/${exercise.id}/external-submission-results?studentLogin=${student.login}`, student, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.resultService.convertDateFromServer(res)));
    }

    // public ResponseEntity<AuxiliaryRepository> createAuxiliaryRepository(@PathVariable Long exerciseId, @RequestBody AuxiliaryRepository repository) {

    save(exercise: Exercise, auxiliaryRepository: AuxiliaryRepository): Observable<EntityResponseType> {
        let auxiliaryRepositoryJSON = JSON.stringify(auxiliaryRepository);
        return this.http.post<AuxiliaryRepository>(`${SERVER_API_URL}api/programming-exercises/${exercise.id}/auxiliary-repository`, auxiliaryRepositoryJSON, {
            observe: 'response',
        });
    }

    save1(exercise: Exercise, repositoryName: String, checkoutDirectory: String, description: String): Observable<EntityResponseType> {
        return this.http
            .post<Result>(`${SERVER_API_URL}api/programming-exercises/${exercise.id}/auxiliary-repository?studentLogin=${repositoryName}`, exercise, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.resultService.convertDateFromServer(res)));
        /*
        return this.http
            .post(`${SERVER_API_URL}api/programming-exercises/${exercise.id}/auxiliary-repository=${student.login}`, copy, { observe: 'response' });

        */
    }
}
