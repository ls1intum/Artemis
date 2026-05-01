import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class ProofExercisePagingService extends ExercisePagingService<ProofExercise> {
    private static readonly RESOURCE_URL = 'api/proof/proof-exercises';

    constructor() {
        const http = inject(HttpClient);
        super(http, ProofExercisePagingService.RESOURCE_URL);
    }
}
