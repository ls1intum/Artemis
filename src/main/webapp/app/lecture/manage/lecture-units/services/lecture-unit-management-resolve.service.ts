import { Service, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';

@Service()
export class AttachmentVideoUnitResolve implements Resolve<AttachmentVideoUnit> {
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);

    resolve(route: ActivatedRouteSnapshot): Observable<AttachmentVideoUnit> {
        const lectureId = route.params['lectureId'];
        const attachmentVideoUnitId = route.params['attachmentVideoUnitId'];
        if (attachmentVideoUnitId) {
            return this.attachmentVideoUnitService.findById(attachmentVideoUnitId, lectureId);
        }
        return of(new AttachmentVideoUnit());
    }
}
