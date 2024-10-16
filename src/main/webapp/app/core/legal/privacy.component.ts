import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { CommonModule } from '@angular/common';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-privacy',
    template: `
        <div [innerHTML]="privacyStatement | htmlForMarkdown"></div>
        @if (isAuthenticated) {
            <a jhiTranslate="artemisApp.dataExport.title" [routerLink]="['/privacy/data-exports']"> </a>
        }
    `,
    standalone: true,
    imports: [TranslateDirective, ArtemisMarkdownModule, CommonModule, ArtemisSharedComponentModule, ArtemisSharedModule, ArtemisMarkdownModule],
})
export class PrivacyComponent implements OnInit, OnDestroy {
    private legalDocumentService = inject(LegalDocumentService);
    private languageHelper = inject(JhiLanguageHelper);
    private accountService = inject(AccountService);

    private languageChangeSubscription?: Subscription;
    privacyStatement?: string;
    isAuthenticated: boolean;

    /**
     * On init get the privacy statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        this.isAuthenticated = this.accountService.isAuthenticated();
        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe((lang) => {
            this.legalDocumentService.getPrivacyStatement(lang as LegalDocumentLanguage).subscribe((statement) => (this.privacyStatement = statement.text));
        });
    }

    ngOnDestroy() {
        if (this.languageChangeSubscription) {
            this.languageChangeSubscription.unsubscribe();
        }
    }
}
