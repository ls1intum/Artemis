import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentRef } from '@angular/core';
import { IrisLogoComponent, IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';

describe('IrisLogoComponent', () => {
    let component: IrisLogoComponent;
    let fixture: ComponentFixture<IrisLogoComponent>;
    let componentRef: ComponentRef<IrisLogoComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(IrisLogoComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should have size IrisLogoSize.BIG and look IrisLogoLookDirection.RIGHT if nothing is provided', () => {
        expect(component.look()).toBe(IrisLogoLookDirection.RIGHT);
        expect(component.size()).toBe(IrisLogoSize.BIG);
    });

    Object.values(IrisLogoSize).forEach((size) => {
        it(`should correctly set the logo size to ${size}`, () => {
            componentRef.setInput('size', size);
            expect(component.size()).toBe(size);

            if (size === IrisLogoSize.SMALL) {
                expect(component.logoUrl()).toBe('public/images/iris/iris-logo-small.png');
            } else {
                expect(component.logoUrl()).toBe(`public/images/iris/iris-logo-big-${component.look()}.png`);
            }
        });
    });

    Object.values(IrisLogoLookDirection).forEach((direction) => {
        it(`should correctly set the look direction to ${direction}`, () => {
            componentRef.setInput('look', direction);
            expect(component.look()).toBe(direction);
            expect(component.logoUrl()).toBe(`public/images/iris/iris-logo-big-${direction}.png`);
        });
    });

    Object.entries({
        [IrisLogoSize.TEXT]: 'iris-logo--text',
        [IrisLogoSize.SMALL]: 'iris-logo--small',
        [IrisLogoSize.MEDIUM]: 'iris-logo--medium',
        [IrisLogoSize.BIG]: 'iris-logo--big',
        [IrisLogoSize.FLUID]: 'iris-logo--fluid',
    }).forEach(([size, expectedClass]) => {
        it(`should return correct class list for size ${size}`, () => {
            componentRef.setInput('size', size as IrisLogoSize);
            expect(component.classList()).toBe(expectedClass);
        });
    });

    it('should provide a translated alternative for a standalone logo', () => {
        expect(fixture.nativeElement.querySelector('img').alt).toBe('artemisApp.iris.logoAlt');
    });

    it('should hide a decorative logo from assistive technology', async () => {
        componentRef.setInput('decorative', true);
        await fixture.whenStable();
        expect(fixture.nativeElement.querySelector('img').alt).toBe('');
    });

    it('should return empty string for custom numeric size', () => {
        componentRef.setInput('size', 100);
        expect(component.classList()).toBe('');
    });
});
