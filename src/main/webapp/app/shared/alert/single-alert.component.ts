import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Alert } from 'app/core/util/alert.service';
import { checkForMissingTranslationKey } from 'app/shared/util/utils';
import { faCheckCircle, faExclamationCircle, faExclamationTriangle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { animate, state, style, transition, trigger } from '@angular/animations';

@Component({
    selector: 'jhi-single-alert',
    templateUrl: './single-alert.component.html',
    styleUrls: ['./single-alert.component.scss'],
    animations: [
        trigger('status', [
            state(
                'start',
                style({
                    height: '0',
                    marginBottom: '0',
                }),
            ),
            state(
                'visible',
                style({
                    height: '*',
                    marginBottom: '15px',
                    opacity: '1',
                }),
            ),
            state(
                'end',
                style({
                    height: '*',
                    opacity: '0',
                    marginBottom: '15px',
                }),
            ),
            transition('start => visible', [animate('0.1s')]),
            transition('visible => end', [animate('0.1s')]),
        ]),
    ],
})
export class SingleAlertComponent implements OnInit {
    @Input()
    alert: Alert;

    infoCircle = faInfoCircle;
    exclamationCircle = faExclamationCircle;
    exclamationTriangle = faExclamationTriangle;
    checkCircle = faCheckCircle;

    internalStatus = 'start';

    @HostBinding('@status') get status() {
        return this.internalStatus;
    }

    ngOnInit() {
        setTimeout(() => (this.internalStatus = 'visible'), 100);
    }

    /**
     * The received alert may contain a message which could not be translated.
     * We slice the wrapping 'translation-not-found[..]' and return the response.
     * @param alert which contains the alert message
     */
    getAlertMessage(alert: Alert) {
        checkForMissingTranslationKey(alert);
        return alert.message;
    }
}
