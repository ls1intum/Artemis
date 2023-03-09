import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
    exports: [FormsModule, CommonModule, NgbModule, FontAwesomeModule, ReactiveFormsModule, TranslateModule],
})
export class ArtemisSharedLibsModule {}
