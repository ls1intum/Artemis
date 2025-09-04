import { Component, input } from '@angular/core';

@Component({
    selector: 'app-league-bronze-icon',
    template: `
        @if (league() === 'Bronze') {
            <svg [class]="class()" [attr.width]="getSize()" [attr.height]="getSize()" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M12 4.3094L5.33975 8.1547V15.8453L12 19.6906L18.6603 15.8453V8.1547L12 4.3094ZM20.6603 7L12 2L3.33975 7V17L12 22L20.6603 17V7Z"
                    fill="hsl(30, 60%, 50%)"
                />
                <path d="M6.80385 12.7991V10.4897L12 13.4897L17.1962 10.4897V12.7991L12 15.7991L6.80385 12.7991Z" fill="hsl(30, 60%, 50%)" />
            </svg>
        }
    `,
})
export class LeagueBronzeIconComponent {
    class = input<string>('');
    league = input<string>('');

    getSize(): string {
        return this.class().includes('small-icon') ? '30' : '50';
    }
}
