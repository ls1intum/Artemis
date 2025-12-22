import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBullhorn, faHashtag, faLock } from '@fortawesome/free-solid-svg-icons';

describe('ChannelIconComponent', () => {
    let component: ChannelIconComponent;
    let fixture: ComponentFixture<ChannelIconComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({ imports: [ChannelIconComponent, FaIconComponent] }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ChannelIconComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should return faBullhorn when isAnnouncementChannel is true', () => {
        fixture.componentRef.setInput('isAnnouncementChannel', true);
        fixture.detectChanges();
        expect(component.getIcon()).toBe(faBullhorn);
    });

    it('should return faHashtag when isPublic is true, and is not announcement', () => {
        fixture.componentRef.setInput('isAnnouncementChannel', false);
        fixture.componentRef.setInput('isPublic', true);
        fixture.detectChanges();
        expect(component.getIcon()).toBe(faHashtag);
    });

    it('should return faLock when isPublic is false, and is not announcement', () => {
        fixture.componentRef.setInput('isAnnouncementChannel', false);
        fixture.componentRef.setInput('isPublic', false);
        fixture.detectChanges();
        expect(component.getIcon()).toBe(faLock);
    });
});
