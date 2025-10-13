import { Component, effect, input, signal } from '@angular/core';

@Component({
    selector: 'app-league-master-icon',
    template: `
        @if (league() === 'Master') {
            <svg [class]="class()" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 26 28" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M13 6.3094L6.33975 10.1547V17.8453L13 21.6906L19.6603 17.8453V10.1547L13 6.3094ZM21.6603 9L13 4L4.33975 9V19L13 24L21.6603 19V9Z"
                    fill="hsl(270, 60%, 50%)"
                />
                <path d="M18.19 17.0036L15.69 18.4469V9.55307L18.19 10.9965V17.0036Z" fill="hsl(270, 60%, 50%)" />
                <path d="M7.81001 10.9964L10.31 9.55307V18.4469L7.81001 17.0036V10.9964Z" fill="hsl(270, 60%, 50%)" />
                <path d="M13 11.1155L11.5 10.4894V15.5913L13 16.2174L14.5 15.5913V10.4894L13 11.1155Z" fill="hsl(270, 60%, 50%)" />
                <path d="M19.1244 5.8453L23.1244 8.1547V12.7735L25.1244 11.6188V7L21.1244 4.6906L19.1244 5.8453Z" fill="hsl(270, 60%, 50%)" />
                <path d="M9 2.3094L13 0L17 2.3094V4.6188L13 2.3094L9 4.6188V2.3094Z" fill="hsl(270, 60%, 50%)" />
                <path d="M0.875641 11.6188V7L4.87564 4.6906L6.87564 5.8453L2.87564 8.1547V12.7735L0.875641 11.6188Z" fill="hsl(270, 60%, 50%)" />
                <path d="M4.87565 23.3094L0.875641 21V16.3812L2.87564 15.2265V19.8453L6.87565 22.1547L4.87565 23.3094Z" fill="hsl(270, 60%, 50%)" />
                <path d="M17 25.6906L13 28L9 25.6906V23.3812L13 25.6906L17 23.3812V25.6906Z" fill="hsl(270, 60%, 50%)" />
                <path d="M25.1244 16.3812V21L21.1244 23.3094L19.1244 22.1547L23.1244 19.8453V15.2265L25.1244 16.3812Z" fill="hsl(270, 60%, 50%)" />
            </svg>
        }
    `,
})
export class LeagueMasterIconComponent {
    class = input<string>('');
    league = input<string>('');
    size = signal('50');

    constructor() {
        effect(() => {
            this.size.set(this.class().includes('small-icon') ? '30' : '50');
        });
    }
}
