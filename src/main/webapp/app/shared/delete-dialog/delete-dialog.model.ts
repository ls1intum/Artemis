import { EventEmitter } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Defines the type of the action handled by delete dialog
 */
export enum ActionType {
    Delete = 'delete',
    Reset = 'reset',
    Cleanup = 'cleanup',
    Remove = 'remove',
    Unlink = 'unlink',
}

/**
 * Data that will be passed to the delete dialog component
 */
export class DeleteDialogData {
    // error message emitted from the component delete method, that will be handled by the dialog
    // when delete method succeeded empty message is sent
    dialogError: Observable<string>;

    // title of the entity we want to delete
    entityTitle: string;

    // i18n key, that will be translated
    deleteQuestion: string;

    // i18n key, if undefined no safety check will take place (input name of the entity)
    deleteConfirmationText?: string;

    // object with check name as a key and i18n key as a value, check names will be used for the return statement
    additionalChecks?: { [key: string]: string };

    // type of the the action that delete dialog will handle
    actionType: ActionType;

    // output event passed to the delete dialog component
    delete: EventEmitter<any>;
}
