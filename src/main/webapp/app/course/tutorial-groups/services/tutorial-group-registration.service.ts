import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroupRegistration } from 'app/entities/tutorial-group/tutorial-group-registration.model';
import { Observable } from 'rxjs';
type EntityResponseType = HttpResponse<TutorialGroupRegistration>;
type EntityArrayResponseType = HttpResponse<TutorialGroupRegistration[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupRegistrationService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getRegistrationsOfUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<TutorialGroupRegistration[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/registrations`, { observe: 'response' });
    }
}
