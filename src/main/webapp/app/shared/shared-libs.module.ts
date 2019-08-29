import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgJhipsterModule } from 'ng-jhipster';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { CookieModule } from 'ngx-cookie';
import { ArtemisIconsModule } from 'app/shared/icons/icons.module';

@NgModule({
    imports: [NgbModule, InfiniteScrollModule, CookieModule.forRoot(), ArtemisIconsModule],
    exports: [FormsModule, CommonModule, NgbModule, NgJhipsterModule, InfiniteScrollModule, ArtemisIconsModule],
})
export class ArtemisSharedLibsModule {
    static forRoot() {
        return {
            ngModule: ArtemisSharedLibsModule,
        };
    }
}
