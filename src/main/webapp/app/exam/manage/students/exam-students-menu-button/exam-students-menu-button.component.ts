import { Component, ElementRef, TemplateRef, input, viewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { TumUiButtonDirective, TumUiButtonSeverity } from '@tumaet/ui-angular';
import { Menu } from 'primeng/menu';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'jhi-exam-students-menu-button',
    standalone: true,
    templateUrl: './exam-students-menu-button.component.html',
    imports: [Menu, TumUiButtonDirective, ArtemisTranslatePipe, TranslateDirective, NgTemplateOutlet],
})
export class ExamStudentsMenuButtonComponent {
    readonly model = input.required<MenuItem[]>();
    readonly label = input.required<string>();
    readonly buttonIconClass = input.required<string>();
    readonly disabled = input(false);
    readonly buttonClass = input('');
    readonly severity = input<TumUiButtonSeverity>('primary');
    readonly endTemplate = input<TemplateRef<unknown>>();

    readonly menu = viewChild<Menu>('menu');
    readonly menuButton = viewChild('menuButton', { read: ElementRef });

    toggleMenu(event: Event): void {
        this.menu()?.toggle(event);
    }

    openMenu(event?: Event): void {
        const target = (event?.currentTarget instanceof HTMLElement ? event.currentTarget : undefined) ?? this.menuButton()?.nativeElement;
        if (!target) {
            return;
        }
        this.menu()?.show({ currentTarget: target });
    }
}
