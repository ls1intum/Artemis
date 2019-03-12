import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSColorSelectorModule } from 'app/components/color-selector/color-selector.module';
import {TagInputModule}from 'ngx-chips';
import { ReactiveFormsModule } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CategorySelectorComponent } from 'app/components/category-selector/category-selector.component';

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSColorSelectorModule, ReactiveFormsModule, TagInputModule, BrowserAnimationsModule],
    declarations: [
        CategorySelectorComponent
    ],
    exports: [
        CategorySelectorComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCategorySelectorModule {
}
