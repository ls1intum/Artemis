import { Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { AttachmentVideoUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';

@Injectable({ providedIn: 'root' })
export class AttachmentUnitResolve implements Resolve<AttachmentVideoUnit> {
    private attachmentUnitService = inject(AttachmentUnitService);

    resolve(route: ActivatedRouteSnapshot): Observable<AttachmentVideoUnit> {
        const lectureId = route.params['lectureId'];
        const attachmentUnitId = route.params['attachmentUnitId'];
        if (attachmentUnitId) {
            return this.attachmentUnitService.findById(attachmentUnitId, lectureId).pipe(
                filter((response: HttpResponse<AttachmentVideoUnit>) => response.ok),
                map((attachmentUnit: HttpResponse<AttachmentVideoUnit>) => attachmentUnit.body!),
            );
        }
        return of(new AttachmentVideoUnit());
    }
}
