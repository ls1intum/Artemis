import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

describe('PostingButtonComponent', () => {
    let component: PostingButtonComponent;
    let fixture: ComponentFixture<PostingButtonComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [PostingButtonComponent, FaIconComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingButtonComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should have icon shown if property set', () => {
        component.buttonIcon = faPlus;
        fixture.changeDetectorRef.detectChanges();
        const button = getElement(debugElement, 'fa-icon');
        expect(button).not.toBeNull();
    });

    it('should not have icon shown if property not set', () => {
        fixture.detectChanges();
        const button = getElement(debugElement, '#icon');
        expect(button).toBeNull();
    });

    it('should show spinner if loading', () => {
        component.buttonLoading = true;
        fixture.changeDetectorRef.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBeFalse();
    });

    it('should not show spinner if not loading', () => {
        component.buttonLoading = false;
        fixture.changeDetectorRef.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBeTrue();
    });
});
