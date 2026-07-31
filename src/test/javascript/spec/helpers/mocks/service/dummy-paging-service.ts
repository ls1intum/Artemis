import { HttpClient } from '@angular/common/http';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';

export class DummyPagingService extends ExercisePagingService<any> {
    constructor(http: HttpClient) {
        super(http, 'test');
    }
}
