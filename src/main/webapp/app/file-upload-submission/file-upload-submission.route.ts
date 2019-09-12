import { Routes, CanDeactivate, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { Injectable } from '@angular/core';

@Injectable()
export class NotSubmittedFileGuard implements CanDeactivate<FileUploadSubmissionComponent> {
    canDeactivate(component: FileUploadSubmissionComponent, route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
        return component.canDeactivate();
    }
}

export const fileUploadSubmissionRoute: Routes = [
    {
        path: 'file-upload-submission/:participationId',
        component: FileUploadSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
