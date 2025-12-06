import { TestBed } from '@angular/core/testing';
import { CourseSettingsComponent } from 'app/core/course/overview/course-settings/course-settings.component';
import { Component } from '@angular/core';

@Component({
    selector: 'jhi-notification-settings',
    template: '<div>notification-settings-stub</div>',
    standalone: true,
})
class NotificationSettingsStubComponent {}

describe('CourseSettingsComponent', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseSettingsComponent, NotificationSettingsStubComponent],
        }).compileComponents();
    });

    it('should render notification settings', () => {
        const fixture = TestBed.createComponent(CourseSettingsComponent);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('notification-settings-stub');
    });
});
