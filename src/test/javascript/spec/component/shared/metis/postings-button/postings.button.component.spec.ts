import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { getElement } from '../../../../helpers/utils/general.utils';

describe('PostingButtonComponent', () => {
    let component: PostingButtonComponent;
    let fixture: ComponentFixture<PostingButtonComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [PostingButtonComponent, MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingButtonComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should have icon shown if property set', () => {
        component.buttonIcon = 'plus';
        fixture.detectChanges();
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
        fixture.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBeFalse();
    });

    it('should not show spinner if not loading', () => {
        component.buttonLoading = false;
        fixture.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBeTrue();
    });
});
