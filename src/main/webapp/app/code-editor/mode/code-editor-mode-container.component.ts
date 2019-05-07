import { ComponentCanDeactivate } from 'src/main/webapp/app/shared';
import { HostListener, ViewChild, OnDestroy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';
import { catchError, flatMap, map, switchMap, tap } from 'rxjs/operators';

import { CodeEditorComponent } from '../';
import { ParticipationService, Participation } from 'src/main/webapp/app/entities/participation';
import { ActivatedRoute } from '@angular/router';

export abstract class CodeEditorContainer implements OnDestroy, ComponentCanDeactivate {
    @ViewChild(CodeEditorComponent) editor: CodeEditorComponent;
    paramSub: Subscription;

    constructor(protected participationService: ParticipationService, private translateService: TranslateService, protected route: ActivatedRoute) {}

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return this.editor.hasUnsavedChanges();
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }
}
