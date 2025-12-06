import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { IrisLogoComponent, IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';

describe('IrisLogoComponent', () => {
    let component: IrisLogoComponent;
    let fixture: ComponentFixture<IrisLogoComponent>;
    let componentRef: ComponentRef<IrisLogoComponent>;

    beforeEach(async () => {
        fixture = TestBed.createComponent(IrisLogoComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        fixture.detectChanges();
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
        [IrisLogoSize.TEXT]: 'text',
        [IrisLogoSize.SMALL]: 'small',
        [IrisLogoSize.MEDIUM]: 'medium',
        [IrisLogoSize.BIG]: 'big img-fluid',
        [IrisLogoSize.FLUID]: 'fluid',
    }).forEach(([size, expectedClass]) => {
        it(`should return correct class list for size ${size}`, () => {
            componentRef.setInput('size', size as IrisLogoSize);
            expect(component.classList()).toBe(expectedClass);
        });
    });

    it('should return empty string for custom numeric size', () => {
        componentRef.setInput('size', 100);
        expect(component.classList()).toBe('');
    });
});
