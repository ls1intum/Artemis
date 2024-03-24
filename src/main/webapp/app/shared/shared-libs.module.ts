import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';

@NgModule({
    exports: [FormsModule, CommonModule, NgbModule, FontAwesomeModule, ReactiveFormsModule, TranslateModule, MatSidenavModule, MatToolbarModule],
})
export class ArtemisSharedLibsModule {}
