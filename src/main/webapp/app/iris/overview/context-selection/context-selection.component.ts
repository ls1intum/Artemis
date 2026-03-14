import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { RippleModule } from 'primeng/ripple';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { faChalkboardUser, faCheck, faFolderOpen, faGraduationCap, faKeyboard, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

interface IrisMenuItem extends MenuItem {
    faIcon?: IconDefinition;
    items?: IrisMenuItem[];
}

@Component({
    selector: 'jhi-context-selection',
    templateUrl: './context-selection.component.html',
    styleUrls: ['./context-selection.component.scss'],
    imports: [ButtonModule, MenuModule, FaIconComponent, RippleModule, InputTextModule, IconFieldModule, InputIconModule, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContextSelectionComponent {
    private readonly allItems: IrisMenuItem[] = [
        {
            separator: true,
        },
        {
            label: 'artemisApp.iris.contextSelection.courseGroup',
            items: [
                {
                    label: 'Test Course',
                    faIcon: faGraduationCap,
                    command: () => {
                        this.menuLabel.set('Test Course');
                        this.menuIcon.set(faGraduationCap);
                    },
                },
            ],
        },
        {
            separator: true,
        },
        {
            label: 'artemisApp.iris.contextSelection.lecturesGroup',
            items: [
                {
                    label: 'Lecture 1',
                    faIcon: faChalkboardUser,
                    command: () => {
                        this.menuLabel.set('Lecture 1');
                        this.menuIcon.set(faChalkboardUser);
                    },
                },
                {
                    label: 'Lecture 2',
                    faIcon: faChalkboardUser,
                    command: () => {
                        this.menuLabel.set('Lecture 2');
                        this.menuIcon.set(faChalkboardUser);
                    },
                },
            ],
        },
        {
            separator: true,
        },
        {
            label: 'artemisApp.iris.contextSelection.exercisesGroup',
            items: [
                {
                    label: 'Exercise 1',
                    faIcon: faKeyboard,
                    command: () => {
                        this.menuLabel.set('Exercise 1');
                        this.menuIcon.set(faKeyboard);
                    },
                },
                {
                    label: 'Exercise 2',
                    faIcon: faKeyboard,
                    command: () => {
                        this.menuLabel.set('Exercise 2');
                        this.menuIcon.set(faKeyboard);
                    },
                },
            ],
        },
    ];

    readonly menuLabel = signal<string>(this.allItems.find((i) => !i.separator && (i.items?.length ?? 0) > 0)?.items![0].label ?? '');
    readonly menuIcon = signal<IconDefinition>(this.allItems.find((i) => !i.separator && (i.items?.length ?? 0) > 0)?.items![0].faIcon ?? faGraduationCap);

    readonly searchTerm = signal<string>('');

    readonly filteredItems = computed<IrisMenuItem[]>(() => {
        const term = this.searchTerm().trim().toLowerCase();
        if (!term) return this.allItems;
        return this.allItems
            .filter((item) => !item.separator)
            .map((group) => ({
                ...group,
                items: group.items?.filter((sub) => sub.label?.toLowerCase().includes(term)),
            }))
            .filter((group) => (group.items?.length ?? 0) > 0);
    });

    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faCheck = faCheck;
}
