import { RouterModule, Routes } from '@angular/router';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { CodeEditorInstructorAndEditorOrionContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-orion-container.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: 'test',
        component: CodeEditorInstructorAndEditorContainerComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'ide/test',
        component: CodeEditorInstructorAndEditorOrionContainerComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'ide/:participationId',
        component: CodeEditorInstructorAndEditorOrionContainerComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':participationId',
        component: CodeEditorInstructorAndEditorContainerComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.editor.home.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCodeEditorManagementRoutingModule {}
