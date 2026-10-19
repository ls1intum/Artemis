import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';

@Service()
export class DummyPagingService extends ExercisePagingService<any> {
    constructor() {
        super(inject(HttpClient), 'test');
    }
}
