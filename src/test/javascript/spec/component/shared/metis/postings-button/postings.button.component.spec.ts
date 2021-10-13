import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { getElement } from '../../../../helpers/utils/general.utils';

describe('PostingsButtonComponent', () => {
    let component: PostingsButtonComponent;
    let fixture: ComponentFixture<PostingsButtonComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [PostingsButtonComponent, MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingsButtonComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should have icon shown if property set', () => {
        component.buttonIcon = 'plus';
        fixture.detectChanges();
        const button = getElement(debugElement, 'fa-icon');
        expect(button).not.toBeNull;
    });

    it('should not have icon shown if property not set', () => {
        fixture.detectChanges();
        const button = getElement(debugElement, '#buttonIcon');
        expect(button).toBeNull;
    });

    it('should show spinner if loading', () => {
        component.buttonLoading = true;
        fixture.detectChanges();
        const button = getElement(debugElement, '#loadingIcon');
        expect(button).not.toBeNull;
    });

    it('should not show spinner if not loading', () => {
        component.buttonLoading = false;
        fixture.detectChanges();
        const button = getElement(debugElement, '#loadingIcon');
        expect(button).toBeNull;
    });
});
