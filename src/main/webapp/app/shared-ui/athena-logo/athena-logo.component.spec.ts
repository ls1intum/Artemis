import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { AthenaLogoComponent } from 'app/shared-ui/athena-logo/athena-logo.component';

describe('AthenaLogoComponent', () => {
    let componentRef: ComponentRef<AthenaLogoComponent>;
    let fixture: ComponentFixture<AthenaLogoComponent>;

    function image(): HTMLImageElement {
        return fixture.nativeElement.querySelector('img');
    }

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [AthenaLogoComponent] }).compileComponents();

        fixture = TestBed.createComponent(AthenaLogoComponent);
        componentRef = fixture.componentRef;
        fixture.detectChanges();
    });

    it('should point at the bundled athena logo', () => {
        expect(image().getAttribute('src')).toBe('public/images/athena/athena-logo.png');
    });

    it('should hide the decorative mark from screen readers', () => {
        // The title next to it already names the feature, so announcing the image would repeat it.
        expect(image().getAttribute('alt')).toBe('');
        expect(image().getAttribute('aria-hidden')).toBe('true');
    });

    it('should render larger than the iris mark by default', () => {
        expect(image().style.height).toBe('28px');
    });

    it('should use the requested height', () => {
        componentRef.setInput('size', 40);
        fixture.detectChanges();

        expect(image().style.height).toBe('40px');
    });
});
