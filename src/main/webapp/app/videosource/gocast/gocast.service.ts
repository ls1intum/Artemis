import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { GocastApprovalStart, GocastBinding } from './gocast.model';

@Injectable({ providedIn: 'root' })
export class GocastService {
    private readonly http = inject(HttpClient);

    getBinding(courseId: number): Observable<GocastBinding> {
        return this.http.get<GocastBinding>(`api/videosource/courses/${courseId}/binding`);
    }

    startApproval(courseId: number): Observable<GocastApprovalStart> {
        return this.http.post<GocastApprovalStart>(`api/videosource/courses/${courseId}/binding/approval`, null);
    }

    unlink(courseId: number): Observable<void> {
        return this.http.delete<void>(`api/videosource/courses/${courseId}/binding`);
    }
}
