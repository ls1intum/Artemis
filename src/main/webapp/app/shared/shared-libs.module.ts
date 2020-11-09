import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgJhipsterModule } from 'ng-jhipster';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ChartsModule } from 'ng2-charts';

@NgModule({
    exports: [FormsModule, CommonModule, NgbModule, NgJhipsterModule, InfiniteScrollModule, FontAwesomeModule, ReactiveFormsModule, TranslateModule, ChartsModule],
})
export class ArtemisSharedLibsModule {}
