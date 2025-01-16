import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { IrisModule } from 'app/iris/iris.module';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IrisGuard } from 'app/iris/iris-guard.service';
import { LectureTranscriptionIngestionComponent } from 'app/admin/lecture-transcription-ingestion/lecture-transcription-ingestion.component';

const routes: Routes = [
    {
        path: 'lecture-transcription-ingestion',
        component: LectureTranscriptionIngestionComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.course',
        },
        canActivate: [UserRouteAccessService, IrisGuard],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), IrisModule],
    exports: [RouterModule],
})
export class LectureTranscriptionIngestionRoutingModule {}
