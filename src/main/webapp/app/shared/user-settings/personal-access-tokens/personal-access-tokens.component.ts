import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

type JwtToken = {
    id_token: string;
};

@Component({
    selector: 'jhi-personal-access-tokens',
    templateUrl: './personal-access-tokens.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class PersonalAccessTokensComponent implements OnInit {
    FeatureToggle = FeatureToggle;
    tokenMaxLifetimeDays: number;
    newTokenLifetimeDays = 1;
    newTokenLifetimeMillis: number;
    wasCopied = false;
    token = '';

    private dayInMillis = 1000 * 60 * 60 * 24;
    private tokenUrl = SERVER_API_URL + 'api/personal-access-token';

    constructor(private http: HttpClient) {
        this.convertTokenLifetimeFromDaysToMillis();
    }

    ngOnInit() {
        this.http.get<number>(this.tokenUrl).subscribe((resp: number) => (this.tokenMaxLifetimeDays = resp / this.dayInMillis));
    }

    convertTokenLifetimeFromDaysToMillis() {
        this.newTokenLifetimeMillis = this.newTokenLifetimeDays * this.dayInMillis;
    }

    generateNewToken() {
        this.http
            .post<JwtToken>(this.tokenUrl, '', { params: { lifetimeMilliseconds: this.newTokenLifetimeMillis } })
            .subscribe((resp: JwtToken) => (this.token = resp['id_token']));
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
