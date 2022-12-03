import { NgModule } from '@angular/core';
import { CourseGroupComponent } from 'app/shared/course-group/course-group.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { UserImportModule } from 'app/shared/import/user-import.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';

@NgModule({
    imports: [ArtemisDataTableModule, UserImportModule, NgxDatatableModule, ArtemisSharedModule, RouterModule],
    declarations: [CourseGroupComponent],
    exports: [CourseGroupComponent],
})
export class ArtemisCourseGroupModule {}
