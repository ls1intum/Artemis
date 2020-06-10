import { Injectable } from '@angular/core';

import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/exams';

    constructor() {}
}
