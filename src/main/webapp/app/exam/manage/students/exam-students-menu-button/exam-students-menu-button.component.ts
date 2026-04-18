import { Component, input, viewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { Menu } from 'primeng/menu';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exam-students-menu-button',
    standalone: true,
    templateUrl: './exam-students-menu-button.component.html',
    imports: [Menu, ButtonDirective, ArtemisTranslatePipe, TranslateDirective],
})
export class ExamStudentsMenuButtonComponent {
    readonly model = input.required<MenuItem[]>();
    readonly label = input.required<string>();
    readonly buttonIconClass = input.required<string>();
    readonly disabled = input(false);
    readonly buttonClass = input('');

    readonly menu = viewChild<Menu>('menu');

    toggleMenu(event: Event): void {
        this.menu()?.toggle(event);
    }
}
