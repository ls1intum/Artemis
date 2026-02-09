import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';

type Language = {
    name: string;
};

@Component({
    selector: 'jhi-tutorial-edit',
    imports: [InputGroupModule, InputGroupAddonModule, InputTextModule, FormsModule, ToggleSwitchModule, SelectModule, InputNumberModule],
    templateUrl: './tutorial-edit.component.html',
    styleUrl: './tutorial-edit.component.scss',
})
export class TutorialEditComponent {
    title = signal('');
    online = signal(false);
    languages: Language[] = [{ name: 'English' }, { name: 'German' }];
    selectedLanguage = signal('English');
    campus = signal('');
    capacity = signal<number | undefined>(undefined);
}
