import { JhiAlertService } from 'ng-jhipster';
import { HttpErrorResponse } from '@angular/common/http';

export abstract class OnError {
    protected constructor(protected jhiAlertService: JhiAlertService) {}

    onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
        // error.headers.get('X-artemisApp-error')
    }
}
