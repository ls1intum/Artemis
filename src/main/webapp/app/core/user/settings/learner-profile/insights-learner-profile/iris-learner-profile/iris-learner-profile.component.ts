import { Component, inject } from '@angular/core';
import { OnInit } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { FormsModule } from '@angular/forms';
import { MemirisMemoriesListComponent } from './memiris-memories-list.component';

@Component({
    selector: 'jhi-iris-learner-profile',
    imports: [TranslateDirective, FormsModule, MemirisMemoriesListComponent],
    templateUrl: './iris-learner-profile.component.html',
})
export class IrisLearnerProfileComponent implements OnInit {
    accountService = inject(AccountService);

    memirisEnabled: boolean;

    ngOnInit(): void {
        this.memirisEnabled = this.accountService.userIdentity?.memirisEnabled ?? false;
    }

    onMemirisEnabledChange() {
        this.accountService.setUserEnabledMemiris(this.memirisEnabled);
    }
}
