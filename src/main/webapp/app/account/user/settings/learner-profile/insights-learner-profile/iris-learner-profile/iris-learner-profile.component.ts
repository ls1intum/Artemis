import { Component, inject, signal } from '@angular/core';
import { OnInit } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { FormsModule } from '@angular/forms';
import { TumUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { MemirisMemoriesListComponent } from './memiris-memories-list.component';

@Component({
    selector: 'jhi-iris-learner-profile',
    imports: [TranslateDirective, FormsModule, TumUiToggleSwitchComponent, MemirisMemoriesListComponent],
    templateUrl: './iris-learner-profile.component.html',
})
export class IrisLearnerProfileComponent implements OnInit {
    accountService = inject(AccountService);

    readonly memirisEnabled = signal(false);

    ngOnInit(): void {
        this.memirisEnabled.set(this.accountService.userIdentity()?.memirisEnabled ?? false);
    }

    onMemirisEnabledChange(enabled: boolean) {
        this.memirisEnabled.set(enabled);
        this.accountService.setUserEnabledMemiris(enabled);
    }
}
