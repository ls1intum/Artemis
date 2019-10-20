import { JhiAlertService } from 'ng-jhipster';
import { HttpErrorResponse } from '@angular/common/http';

export abstract class OnError {
    protected constructor(protected jhiAlertService: JhiAlertService) {}

    onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
        // TODO: Check why these implementations are needed?
        // error.headers.get('X-artemisApp-error')
        //                 const errorMessage = error.headers.get('X-artemisApp-alert');
    }
}
