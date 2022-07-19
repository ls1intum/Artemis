import { Component, OnInit } from '@angular/core';
import { PersonalAccessTokenService } from 'app/shared/user-settings/personal-access-tokens/personal-access-tokens.service';

@Component({
    selector: 'jhi-personal-access-tokens',
    templateUrl: './personal-access-tokens.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class PersonalAccessTokensComponent implements OnInit {
    tokenMaxLifetimeDays: number;
    newTokenLifetimeDays = 1;
    wasCopied = false;
    token = '';

    constructor(private patService: PersonalAccessTokenService) {}

    ngOnInit() {
        this.patService.getTokenMaxLifetimeDays().subscribe((resp: number) => (this.tokenMaxLifetimeDays = resp));
    }

    generateNewToken() {
        this.patService.generateNewToken(this.newTokenLifetimeDays).subscribe((token: string) => (this.token = token));
    }

    /**
     * set wasCopied for 3 seconds on success
     */
    onCopyFinished(successful: boolean) {
        if (successful) {
            this.wasCopied = true;
            setTimeout(() => {
                this.wasCopied = false;
            }, 3000);
        }
    }
}
