import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgSelectModule } from '@ng-select/ng-select';

@NgModule({
    exports: [FormsModule, CommonModule, NgbModule, NgSelectModule, FontAwesomeModule, ReactiveFormsModule, TranslateModule],
})
export class ArtemisSharedLibsModule {}
