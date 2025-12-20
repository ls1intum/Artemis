/**
 * Vitest tests for ExternalUserPasswordResetModalComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExternalUserPasswordResetModalComponent } from 'app/core/account/password-reset/external/external-user-password-reset-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('ExternalUserPasswordResetModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExternalUserPasswordResetModalComponent>;
    let comp: ExternalUserPasswordResetModalComponent;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExternalUserPasswordResetModalComponent],
            providers: [NgbActiveModal],
        })
            .overrideTemplate(ExternalUserPasswordResetModalComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExternalUserPasswordResetModalComponent);
        comp = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
    });

    it('should create the component', () => {
        expect(comp).toBeDefined();
    });

    it('should allow setting externalCredentialProvider', () => {
        comp.externalCredentialProvider = 'LDAP';
        expect(comp.externalCredentialProvider).toBe('LDAP');
    });

    it('should allow setting externalPasswordResetLink', () => {
        comp.externalPasswordResetLink = 'https://example.com/reset';
        expect(comp.externalPasswordResetLink).toBe('https://example.com/reset');
    });

    describe('clear', () => {
        it('should dismiss the modal when clear is called', () => {
            const dismissSpy = vi.spyOn(activeModal, 'dismiss');

            comp.clear();

            expect(dismissSpy).toHaveBeenCalledOnce();
        });
    });
});
