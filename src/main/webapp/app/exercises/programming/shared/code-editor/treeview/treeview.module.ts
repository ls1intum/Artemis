import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TreeviewComponent } from './components/treeview/treeview.component';
import { TreeviewItemComponent } from './components/treeview-item/treeview-item.component';

@NgModule({
    imports: [FormsModule, CommonModule],
    declarations: [TreeviewComponent, TreeviewItemComponent],
    exports: [TreeviewComponent],
})
export class TreeviewModule {
    static forRoot(): ModuleWithProviders<TreeviewModule> {
        return {
            ngModule: TreeviewModule,
        };
    }
}
