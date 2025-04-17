import { Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';

@Injectable({ providedIn: 'root' })
export class AttachmentVideoUnitResolve implements Resolve<AttachmentVideoUnit> {
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);

    resolve(route: ActivatedRouteSnapshot): Observable<AttachmentVideoUnit> {
        const lectureId = route.params['lectureId'];
        const attachmentVideoUnitId = route.params['attachmentUnitId'];
        if (attachmentVideoUnitId) {
            return this.attachmentVideoUnitService.findById(attachmentVideoUnitId, lectureId).pipe(
                filter((response: HttpResponse<AttachmentVideoUnit>) => response.ok),
                map((attachmentVideoUnit: HttpResponse<AttachmentVideoUnit>) => attachmentVideoUnit.body!),
            );
        }
        return of(new AttachmentVideoUnit());
    }
}
