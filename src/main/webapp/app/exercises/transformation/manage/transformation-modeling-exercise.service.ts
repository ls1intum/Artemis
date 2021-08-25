import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { TransformationModelingExercise } from 'app/entities/transformation-modeling-exercise.model';

export type EntityResponseType = HttpResponse<TransformationModelingExercise>;
export type EntityArrayResponseType = HttpResponse<TransformationModelingExercise[]>;

@Injectable({ providedIn: 'root' })
export class TransformationModelingExerciseService {
    public resourceUrl = SERVER_API_URL + 'api/transformation-modeling-exercises';

    constructor(private http: HttpClient) {}

    fetchSolution(problem: UMLModel): Observable<UMLModel> {
        return this.http.post<UMLModel>(`api/reachability-graph`, problem, { observe: 'response' }).pipe(map((res: HttpResponse<UMLModel>) => res.body!));
    }
}
