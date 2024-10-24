import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

describe('ProfilePictureComponent', () => {
    let component: ProfilePictureComponent;
    let fixture: ComponentFixture<ProfilePictureComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProfilePictureComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ProfilePictureComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).not.toBeNull();
    });

    it('should update initials on change', () => {
        const authorNameNew = 'Test Tester';
        const authorInitials = 'TT';
        expect(component.userProfilePictureInitials).not.toBe(authorInitials);
        fixture.componentRef.setInput('authorName', authorNameNew);
        fixture.detectChanges();
        expect(component.userProfilePictureInitials).toBe(authorInitials);
    });

    it('should update default image on change', () => {
        const authorId = '1234';
        const hueToDetect = 'hsl(144.48424688749213, 50%, 50%)';
        expect(component.profilePictureBackgroundColor).not.toBe(hueToDetect);
        fixture.componentRef.setInput('authorId', authorId);
        fixture.detectChanges();
        expect(component.profilePictureBackgroundColor).toBe(hueToDetect);
    });

    it('should update image size and font size on change', () => {
        const imageSize = '100';
        const fontSize = '100';
        expect(component.imageSize).not.toBe(imageSize + 'rem');
        expect(component.fontSize).not.toBe(fontSize + 'rem');
        fixture.componentRef.setInput('imageSizeInRem', imageSize);
        fixture.componentRef.setInput('fontSizeInRem', fontSize);
        fixture.detectChanges();
        expect(component.imageSize).toBe(imageSize + 'rem');
        expect(component.fontSize).toBe(fontSize + 'rem');
    });
});
