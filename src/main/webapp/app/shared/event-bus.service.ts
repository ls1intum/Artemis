import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class EventBusService {
    private lectureDeletedSource = new Subject<number>();
    lectureDeleted$ = this.lectureDeletedSource.asObservable();

    emitLectureDeleted(lectureId: number) {
        this.lectureDeletedSource.next(lectureId);
    }
}
