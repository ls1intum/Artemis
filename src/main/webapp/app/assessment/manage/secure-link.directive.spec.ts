import { beforeEach, describe, expect, it } from 'vitest';
import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, DirectiveFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';

/**
 * The directive does its work in its constructor, so checking that it leaves an existing href alone needs the attribute
 * on the element before the directive is created. Angular writes a template's static attributes first, which
 * TestBed.createDirective cannot do; this template also puts two links on a page.
 */
@Component({
    template: `
        <a jhiSecureLink href="https://example1.com">Link 1</a>
        <a jhiSecureLink href="https://example2.com">Link 2</a>
    `,
    imports: [SecureLinkDirective],
})
class MultiLinkTestHostComponent {}

describe('SecureLinkDirective', () => {
    describe('single link', () => {
        let fixture: DirectiveFixture<SecureLinkDirective>;
        let linkElement: HTMLAnchorElement;

        beforeEach(() => {
            fixture = TestBed.createDirective(SecureLinkDirective, { tagName: 'a' });
            fixture.detectChanges();
            linkElement = fixture.nativeElement as HTMLAnchorElement;
        });

        it('should create directive', () => {
            expect(fixture.directiveInstance).toBeInstanceOf(SecureLinkDirective);
        });

        it('should set target to _blank', () => {
            expect(linkElement.target).toBe('_blank');
        });

        it('should set rel to noopener noreferrer', () => {
            expect(linkElement.rel).toBe('noopener noreferrer');
        });
    });

    describe('links written in a template', () => {
        let fixture: ComponentFixture<MultiLinkTestHostComponent>;
        let linkElements: DebugElement[];

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [MultiLinkTestHostComponent],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(MultiLinkTestHostComponent);
                    fixture.detectChanges();
                    linkElements = fixture.debugElement.queryAll(By.directive(SecureLinkDirective));
                });
        });

        it('should preserve the original href attribute', () => {
            expect(linkElements[0].nativeElement.href).toBe('https://example1.com/');
            expect(linkElements[1].nativeElement.href).toBe('https://example2.com/');
        });

        it('should apply directive to all links', () => {
            expect(linkElements).toHaveLength(2);
        });

        it('should set target to _blank on all links', () => {
            linkElements.forEach((link) => {
                expect(link.nativeElement.target).toBe('_blank');
            });
        });

        it('should set rel to noopener noreferrer on all links', () => {
            linkElements.forEach((link) => {
                expect(link.nativeElement.rel).toBe('noopener noreferrer');
            });
        });
    });
});
