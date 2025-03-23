import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/lecture.service';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/manage/attachment.service';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, filter, map, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    private lectureService = inject(LectureService);

    resolve(route: ActivatedRouteSnapshot): Observable<Lecture> {
        const lectureId = route.params['lectureId'];
        if (lectureId) {
            return this.lectureService.find(lectureId).pipe(
                filter((response: HttpResponse<Lecture>) => response.ok),
                map((lecture: HttpResponse<Lecture>) => lecture.body!),
            );
        }
        return of(new Lecture());
    }
}

@Injectable({ providedIn: 'root' })
export class AttachmentResolve implements Resolve<Attachment> {
    private attachmentService = inject(AttachmentService);

    resolve(route: ActivatedRouteSnapshot): Observable<Attachment> {
        const attachmentId = route.params['attachmentId'];
        if (attachmentId) {
            return this.attachmentService.find(attachmentId).pipe(
                filter((response: HttpResponse<Attachment>) => response.ok),
                map((attachment: HttpResponse<Attachment>) => attachment.body!),
            );
        }
        return of(new Attachment());
    }
}
