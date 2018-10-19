import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { FileUploadSubmission } from 'app/shared/model/file-upload-submission.model';
import { FileUploadSubmissionService } from './file-upload-submission.service';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { FileUploadSubmissionDetailComponent } from './file-upload-submission-detail.component';
import { FileUploadSubmissionUpdateComponent } from './file-upload-submission-update.component';
import { FileUploadSubmissionDeletePopupComponent } from './file-upload-submission-delete-dialog.component';
import { IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

@Injectable({ providedIn: 'root' })
export class FileUploadSubmissionResolve implements Resolve<IFileUploadSubmission> {
    constructor(private service: FileUploadSubmissionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((fileUploadSubmission: HttpResponse<FileUploadSubmission>) => fileUploadSubmission.body));
        }
        return of(new FileUploadSubmission());
    }
}

export const fileUploadSubmissionRoute: Routes = [
    {
        path: 'file-upload-submission',
        component: FileUploadSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'file-upload-submission/:id/view',
        component: FileUploadSubmissionDetailComponent,
        resolve: {
            fileUploadSubmission: FileUploadSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'file-upload-submission/new',
        component: FileUploadSubmissionUpdateComponent,
        resolve: {
            fileUploadSubmission: FileUploadSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'file-upload-submission/:id/edit',
        component: FileUploadSubmissionUpdateComponent,
        resolve: {
            fileUploadSubmission: FileUploadSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const fileUploadSubmissionPopupRoute: Routes = [
    {
        path: 'file-upload-submission/:id/delete',
        component: FileUploadSubmissionDeletePopupComponent,
        resolve: {
            fileUploadSubmission: FileUploadSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
