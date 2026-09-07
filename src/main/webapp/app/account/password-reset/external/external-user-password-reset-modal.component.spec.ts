/**
 * Vitest tests for ExternalUserPasswordResetModalComponent.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExternalUserPasswordResetModalComponent } from 'app/account/password-reset/external/external-user-password-reset-modal.component';

describe('ExternalUserPasswordResetModalComponent', () => {
    let fixture: ComponentFixture<ExternalUserPasswordResetModalComponent>;
    let comp: ExternalUserPasswordResetModalComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExternalUserPasswordResetModalComponent],
        })
            .overrideTemplate(ExternalUserPasswordResetModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExternalUserPasswordResetModalComponent);
        comp = fixture.componentInstance;
    });

    it('should create the component', () => {
        expect(comp).toBeDefined();
    });

    it('should allow setting externalCredentialProvider via input', () => {
        fixture.componentRef.setInput('externalCredentialProvider', 'LDAP');
        fixture.detectChanges();
        expect(comp.externalCredentialProvider()).toBe('LDAP');
    });

    it('should allow setting externalPasswordResetLink via input', () => {
        fixture.componentRef.setInput('externalPasswordResetLink', 'https://example.com/reset');
        fixture.detectChanges();
        expect(comp.externalPasswordResetLink()).toBe('https://example.com/reset');
    });

    describe('dismiss', () => {
        it('should set visible to false when dismiss is called', () => {
            comp.visible.set(true);

            comp.dismiss();

            expect(comp.visible()).toBe(false);
        });
    });

    /**
     * The suite above renders no template, and the dialog itself only appears for a user whose credentials live
     * with an external provider — so nothing else covers this markup.
     */
    describe('template', () => {
        let templateFixture: ComponentFixture<ExternalUserPasswordResetModalComponent>;

        // The dialog renders into the CDK overlay container, not into the fixture element.
        const dialog = () => document.querySelector('.tum-ui-dialog') as HTMLElement;

        beforeEach(async () => {
            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports: [ExternalUserPasswordResetModalComponent, FontAwesomeTestingModule],
                providers: [{ provide: TranslateService, useClass: MockTranslateService }],
            }).compileComponents();

            templateFixture = TestBed.createComponent(ExternalUserPasswordResetModalComponent);
            templateFixture.componentRef.setInput('externalCredentialProvider', 'LDAP');
        });

        afterEach(() => {
            templateFixture.destroy();
        });

        it('offers the reset link when the provider published one', () => {
            templateFixture.componentRef.setInput('externalPasswordResetLink', 'https://example.com/reset');
            templateFixture.componentInstance.visible.set(true);
            templateFixture.detectChanges();

            const link = dialog().querySelector('a') as HTMLAnchorElement;
            expect(link.getAttribute('href')).toBe('https://example.com/reset');
            expect(link.getAttribute('rel')).toContain('noopener');
        });

        it('explains the missing link instead when the provider published none', () => {
            templateFixture.componentInstance.visible.set(true);
            templateFixture.detectChanges();

            expect(dialog().querySelector('a')).toBeNull();
            expect(dialog().textContent).toContain('reset.request.external.noLink');
        });

        it('closes from the confirm button in the footer', () => {
            templateFixture.componentInstance.visible.set(true);
            templateFixture.detectChanges();

            (dialog().querySelector('.tum-ui-dialog-footer button') as HTMLButtonElement).click();
            templateFixture.detectChanges();

            expect(templateFixture.componentInstance.visible()).toBe(false);
        });
    });
});
