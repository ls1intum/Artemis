/**
 * Vitest tests for HealthModalComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HealthModalComponent } from 'app/core/admin/health/health-modal.component';
import { HealthDetails, HealthKey } from 'app/core/admin/health/health.model';

describe('HealthModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HealthModalComponent>;
    let comp: HealthModalComponent;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HealthModalComponent],
            providers: [NgbActiveModal],
        })
            .overrideTemplate(HealthModalComponent, '<button class="btn-close" (click)="dismiss()"></button>')
            .compileComponents();

        fixture = TestBed.createComponent(HealthModalComponent);
        comp = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
    });

    it('should convert basic types to string', () => {
        expect(comp.readableValue(42)).toBe('42');
    });

    it('should stringify objects to parsable JSON', () => {
        const object = {
            foo: 'bar',
            bar: 42,
        };
        const result = comp.readableValue(object);
        expect(JSON.parse(result)).toEqual(object);
    });

    it('should parse GB-value to String for diskSpace', () => {
        comp.health = { key: 'diskSpace' as HealthKey, value: {} as HealthDetails };
        const gbValueInByte = 4156612385;
        const expectedString = '3.87 GB';
        expect(comp.readableValue(gbValueInByte)).toBe(expectedString);
    });

    it('should parse MB-value to String for diskSpace', () => {
        comp.health = { key: 'diskSpace' as HealthKey, value: {} as HealthDetails };
        const mbValueInByte = 41566;
        const expectedString = '0.04 MB';
        expect(comp.readableValue(mbValueInByte)).toBe(expectedString);
    });

    it('should dismiss the modal when dismiss is called', () => {
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');

        comp.dismiss();

        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
