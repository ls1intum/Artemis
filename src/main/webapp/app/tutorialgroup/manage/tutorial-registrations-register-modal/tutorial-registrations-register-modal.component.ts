import { Component, computed, inject, signal } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { FormsModule } from '@angular/forms';
import { IconFieldModule } from 'primeng/iconfield';

@Component({
    selector: 'jhi-tutorial-registrations-register-modal',
    imports: [Dialog, AutoCompleteModule, FormsModule, IconFieldModule],
    templateUrl: './tutorial-registrations-register-modal.component.html',
    styleUrl: './tutorial-registrations-register-modal.component.scss',
})
export class TutorialRegistrationsRegisterModalComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    isOpen = signal(false);
    searchString = signal<string>('');
    suggestedStudents = signal<string[]>([]);
    header = computed<string>(() => this.computeHeader());
    searchBarPlaceholder = computed<string>(() => this.computeSearchBarPlaceholder());

    open() {
        this.isOpen.set(true);
    }

    search(event: AutoCompleteCompleteEvent) {}

    private computeHeader(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.registerModal.header');
    }

    private computeSearchBarPlaceholder(): string {
        return 'Search by Login or Name';
    }
}
