import { HttpResponse } from '@angular/common/http';
import { Posting } from 'app/entities/metis/posting.model';
import { Observable } from 'rxjs';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

export abstract class PostingService<T extends Posting> {
    abstract create(courseId: number, posting: T): Observable<HttpResponse<T>>;

    abstract delete(courseId: number, posting: T): Observable<HttpResponse<any>>;

    abstract update(courseId: number, posting: T): Observable<HttpResponse<T>>;

    /**
     * takes a posting and converts the date from the client
     * @param   {T} posting
     * @return  {T}
     */
    protected convertPostingDateFromClient(posting: T): T {
        return {
            ...posting,
            creationDate: convertDateFromClient(posting.creationDate),
        };
    }

    /**
     * takes a posting and converts the date from the server
     * @param   {HttpResponse<T>} res
     * @return  {HttpResponse<T>}
     */
    protected convertPostingResponseDateFromServer(res: HttpResponse<T>): HttpResponse<T> {
        if (res.body) {
            res.body.creationDate = convertDateFromServer(res.body.creationDate);
        }
        return res;
    }
}
