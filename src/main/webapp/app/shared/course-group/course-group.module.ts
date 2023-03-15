import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';

import { CourseGroupComponent } from 'app/shared/course-group/course-group.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { UserImportModule } from 'app/shared/import/user-import.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisDataTableModule, UserImportModule, NgxDatatableModule, ArtemisSharedModule, RouterModule],
    declarations: [CourseGroupComponent],
    exports: [CourseGroupComponent],
})
export class ArtemisCourseGroupModule {}
