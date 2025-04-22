import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { input, runInInjectionContext } from '@angular/core';
import { faBullhorn, faHashtag, faLock } from '@fortawesome/free-solid-svg-icons';

describe('ChannelIconComponent', () => {
    let component: ChannelIconComponent;
    let fixture: ComponentFixture<ChannelIconComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ declarations: [ChannelIconComponent, MockComponent(FaIconComponent)] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelIconComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should return faBullhorn when isAnnouncementChannel is true', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isAnnouncementChannel = input<boolean>(true);
        });
        expect(component.getIcon()).toBe(faBullhorn);
    });

    it('should return faHashtag when isPublic is true, and is not announcement', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isAnnouncementChannel = input<boolean>(false);
            component.isPublic = input<boolean>(true);
        });
        expect(component.getIcon()).toBe(faHashtag);
    });

    it('should return faLock when isPublic is false, and is not announcement', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isAnnouncementChannel = input<boolean>(false);
            component.isPublic = input<boolean>(false);
        });
        expect(component.getIcon()).toBe(faLock);
    });
});
