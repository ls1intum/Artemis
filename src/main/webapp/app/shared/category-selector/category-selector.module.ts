import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { MatChipsModule } from '@angular/material/chips';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';

@NgModule({
    imports: [ArtemisSharedModule, ReactiveFormsModule, FormsModule, MatChipsModule, MatAutocompleteModule, MatSelectModule, MatFormFieldModule, CategorySelectorComponent],
    exports: [CategorySelectorComponent],
})
export class ArtemisCategorySelectorModule {}
