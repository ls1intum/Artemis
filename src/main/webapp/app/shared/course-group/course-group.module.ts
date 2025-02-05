import { NgModule } from '@angular/core';
import { CourseGroupComponent } from 'app/shared/course-group/course-group.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@NgModule({
    imports: [ArtemisDataTableModule, NgxDatatableModule, ArtemisSharedModule, RouterModule, ProfilePictureComponent, CourseGroupComponent],
    exports: [CourseGroupComponent],
})
export class ArtemisCourseGroupModule {}
