import { Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/manage/lecture-units/attachmentUnit.service';

@Injectable({ providedIn: 'root' })
export class AttachmentUnitResolve implements Resolve<AttachmentUnit> {
    private attachmentUnitService = inject(AttachmentUnitService);

    resolve(route: ActivatedRouteSnapshot): Observable<AttachmentUnit> {
        const lectureId = route.params['lectureId'];
        const attachmentUnitId = route.params['attachmentUnitId'];
        if (attachmentUnitId) {
            return this.attachmentUnitService.findById(attachmentUnitId, lectureId).pipe(
                filter((response: HttpResponse<AttachmentUnit>) => response.ok),
                map((attachmentUnit: HttpResponse<AttachmentUnit>) => attachmentUnit.body!),
            );
        }
        return of(new AttachmentUnit());
    }
}
