import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Posting } from 'app/entities/metis/posting.model';
import { Observable } from 'rxjs';

export abstract class PostingService<T extends Posting> {
    abstract create(courseId: number, posting: T): Observable<HttpResponse<T>>;

    abstract delete(courseId: number, posting: T): Observable<HttpResponse<any>>;

    abstract update(courseId: number, posting: T): Observable<HttpResponse<T>>;

    /**
     * takes a posting and converts the date from the client
     * @param   {T} posting
     * @return  {T}
     */
    protected convertDateFromClient(posting: T): T {
        return {
            ...posting,
            creationDate: posting.creationDate && dayjs(posting.creationDate).isValid() ? dayjs(posting.creationDate).toJSON() : undefined,
        };
    }

    /**
     * takes a posting and converts the date from the server
     * @param   {HttpResponse<T>} res
     * @return  {HttpResponse<T>}
     */
    protected convertDateFromServer(res: HttpResponse<T>): HttpResponse<T> {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? dayjs(res.body.creationDate) : undefined;
        }
        return res;
    }
}
