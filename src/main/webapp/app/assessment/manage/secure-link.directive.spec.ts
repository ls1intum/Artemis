import { beforeEach, describe, expect, it } from 'vitest';
import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';

@Component({
    template: '<a jhiSecureLink href="https://example.com">Test Link</a>',
    imports: [SecureLinkDirective],
})
class TestHostComponent {}

@Component({
    template: `
        <a jhiSecureLink href="https://example1.com">Link 1</a>
        <a jhiSecureLink href="https://example2.com">Link 2</a>
    `,
    imports: [SecureLinkDirective],
})
class MultiLinkTestHostComponent {}

describe('SecureLinkDirective', () => {
    setupTestBed({ zoneless: true });

    describe('single link', () => {
        let fixture: ComponentFixture<TestHostComponent>;
        let linkElement: DebugElement;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [TestHostComponent],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(TestHostComponent);
                    fixture.detectChanges();
                    linkElement = fixture.debugElement.query(By.directive(SecureLinkDirective));
                });
        });

        it('should create directive', () => {
            expect(linkElement).toBeTruthy();
        });

        it('should set target to _blank', () => {
            expect(linkElement.nativeElement.target).toBe('_blank');
        });

        it('should set rel to noopener noreferrer', () => {
            expect(linkElement.nativeElement.rel).toBe('noopener noreferrer');
        });

        it('should preserve the original href attribute', () => {
            expect(linkElement.nativeElement.href).toBe('https://example.com/');
        });
    });

    describe('multiple links', () => {
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
