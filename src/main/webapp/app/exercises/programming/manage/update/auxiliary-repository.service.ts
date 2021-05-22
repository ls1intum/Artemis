import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { EntityResponseType } from 'app/exercises/shared/result/result.service';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class AuxiliaryRepositoryService {
    constructor(private http: HttpClient) {}

    save(exercise: Exercise, auxiliaryRepository: AuxiliaryRepository): Observable<EntityResponseType> {
        return this.http.post<AuxiliaryRepository>(`${SERVER_API_URL}api/programming-exercises/${exercise.id}/auxiliary-repository`, auxiliaryRepository, { observe: 'response' });
    }

    loadAuxiliaryRepositories(exerciseId: number): Observable<AuxiliaryRepository[]> {
        return this.http.get<AuxiliaryRepository[]>(`${SERVER_API_URL}api/programming-exercises/${exerciseId}/auxiliary-repository`);
    }

    updateAuxiliaryRepositories(programmingExercise: ProgrammingExercise) {
        this.loadAuxiliaryRepositories(programmingExercise.id!).subscribe((auxRepos) => {
            programmingExercise.auxiliaryRepositories = auxRepos;
        });
    }
}
