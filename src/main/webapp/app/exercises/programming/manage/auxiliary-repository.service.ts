import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { EntityResponseType } from 'app/exercises/shared/result/result.service';
import { AuxiliaryRepository } from 'app/entities/auxiliary-repository-model';

@Injectable({ providedIn: 'root' })
export class AuxiliaryRepositoryService {
    constructor(private http: HttpClient) {}

    save(exercise: Exercise, auxiliaryRepository: AuxiliaryRepository): Observable<EntityResponseType> {
        return this.http.post<AuxiliaryRepository>(`${SERVER_API_URL}api/programming-exercises/${exercise.id}/auxiliary-repository`, auxiliaryRepository, {
            observe: 'response',
        });
    }
}
