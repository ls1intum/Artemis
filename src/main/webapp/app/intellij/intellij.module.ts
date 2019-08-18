import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IdeFilterDirective } from 'app/intellij/ide-filter.directive';
import { IntellijButtonComponent } from 'app/intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@NgModule({
    declarations: [IdeFilterDirective, IntellijButtonComponent],
    imports: [CommonModule, FontAwesomeModule],
    exports: [IdeFilterDirective, IntellijButtonComponent],
})
export class IntellijModule {}
