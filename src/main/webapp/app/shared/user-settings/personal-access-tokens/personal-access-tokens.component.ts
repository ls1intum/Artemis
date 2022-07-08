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
    newTokenLifetimeSeconds: number;
    wasCopied = false;
    token = '';

    private dayInSeconds = 60 * 60 * 24;

    constructor(private patService: PersonalAccessTokenService) {
        this.convertTokenLifetimeFromDaysToSeconds();
    }

    ngOnInit() {
        this.patService.getTokenMaxLifetimeSeconds().subscribe((resp: number) => (this.tokenMaxLifetimeDays = resp / this.dayInSeconds));
    }

    convertTokenLifetimeFromDaysToSeconds() {
        this.newTokenLifetimeSeconds = this.newTokenLifetimeDays * this.dayInSeconds;
    }

    generateNewToken() {
        this.patService.generateNewToken(this.newTokenLifetimeSeconds).subscribe((token: string) => (this.token = token));
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
