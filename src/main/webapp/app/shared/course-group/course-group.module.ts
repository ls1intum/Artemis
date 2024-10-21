import { NgModule } from '@angular/core';
import { CourseGroupComponent } from 'app/shared/course-group/course-group.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { UserImportModule } from 'app/shared/user-import/user-import.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@NgModule({
    imports: [ArtemisDataTableModule, UserImportModule, NgxDatatableModule, ArtemisSharedModule, RouterModule, ProfilePictureComponent],
    declarations: [CourseGroupComponent],
    exports: [CourseGroupComponent],
})
export class ArtemisCourseGroupModule {}
