import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisColorSelectorModule, ReactiveFormsModule, FormsModule, MatChipsModule, MatAutocompleteModule, MatSelectModule, MatFormFieldModule],
    declarations: [CategorySelectorComponent],
    exports: [CategorySelectorComponent],
})
export class ArtemisCategorySelectorModule {}
