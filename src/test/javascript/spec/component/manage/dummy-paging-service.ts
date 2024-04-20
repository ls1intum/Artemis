import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class DummyPagingService extends ExercisePagingService<any> {
    constructor(http: HttpClient) {
        super(http, 'test');
    }
}
