import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';
import { Injectable } from '@angular/core';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

type EntityArrayResponseType = HttpResponse<LectureUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class LectureUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private attachmentService: AttachmentService, private exerciseService: ExerciseService) {}

    updateOrder(lectureId: number, lectureUnits: LectureUnit[]): Observable<HttpResponse<LectureUnit[]>> {
        return this.httpClient
            .put<LectureUnit[]>(`${this.resourceURL}/lectures/${lectureId}/lecture-units-order`, lectureUnits, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServerResponse(res)));
    }

    delete(lectureUnitId: number) {
        return this.httpClient.delete(`${this.resourceURL}/lecture-units/${lectureUnitId}`, { observe: 'response' });
    }

    convertDateArrayFromClient<T extends LectureUnit>(lectureUnits: T[]): T[] {
        if (lectureUnits && lectureUnits.length > 0) {
            for (let _i = 0; _i < lectureUnits.length; _i++) {
                lectureUnits[_i] = this.convertDateFromClient(lectureUnits[_i]);
            }
        }
        return lectureUnits;
    }

    convertDateFromClient<T extends LectureUnit>(lectureUnit: T): T {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            if ((<AttachmentUnit>lectureUnit).attachment) {
                (<AttachmentUnit>lectureUnit).attachment = this.attachmentService.convertDateFromClient((<AttachmentUnit>lectureUnit).attachment!);
                return lectureUnit;
            }
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            if ((<ExerciseUnit>lectureUnit).exercise) {
                (<ExerciseUnit>lectureUnit).exercise = this.exerciseService.convertDateFromClient((<ExerciseUnit>lectureUnit).exercise!);
                return lectureUnit;
            }
        }
        return Object.assign({}, lectureUnit, {
            releaseDate: lectureUnit.releaseDate && moment(lectureUnit.releaseDate).isValid() ? lectureUnit.releaseDate.toJSON() : undefined,
        });
    }

    convertDateFromServerResponse<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
        if (res.body) {
            if (res.body.type === LectureUnitType.ATTACHMENT) {
                if ((<AttachmentUnit>res.body).attachment) {
                    (<AttachmentUnit>res.body).attachment = this.attachmentService.convertAttachmentDateFromServer((<AttachmentUnit>res.body).attachment);
                }
            } else if (res.body.type === LectureUnitType.EXERCISE) {
                if ((<ExerciseUnit>res.body).exercise) {
                    (<ExerciseUnit>res.body).exercise = this.exerciseService.convertExerciseDateFromServer((<ExerciseUnit>res.body).exercise);
                }
            } else {
                res.body.releaseDate = res.body.releaseDate ? moment(res.body.releaseDate) : undefined;
            }
        }
        return res;
    }

    convertDateFromServerEntity<T extends LectureUnit>(lectureUnit: T): T {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            if ((<AttachmentUnit>lectureUnit).attachment) {
                (<AttachmentUnit>lectureUnit).attachment = this.attachmentService.convertAttachmentDateFromServer((<AttachmentUnit>lectureUnit).attachment);
            }
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            if ((<ExerciseUnit>lectureUnit).exercise) {
                (<ExerciseUnit>lectureUnit).exercise = this.exerciseService.convertExerciseDateFromServer((<ExerciseUnit>lectureUnit).exercise);
            }
        } else {
            lectureUnit.releaseDate = lectureUnit.releaseDate ? moment(lectureUnit.releaseDate) : undefined;
        }
        return lectureUnit;
    }

    convertDateArrayFromServerResponse<T extends LectureUnit>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        if (res.body) {
            res.body.forEach((lectureUnit: LectureUnit) => {
                this.convertDateFromServerEntity(lectureUnit);
            });
        }
        return res;
    }

    convertDateArrayFromServerEntity<T extends LectureUnit>(res: T[]): T[] {
        if (res) {
            res.forEach((lectureUnit: LectureUnit) => {
                this.convertDateFromServerEntity(lectureUnit);
            });
        }
        return res;
    }
}
