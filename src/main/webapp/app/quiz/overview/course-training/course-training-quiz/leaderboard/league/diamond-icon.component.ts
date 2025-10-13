import { Component, effect, input, signal } from '@angular/core';

@Component({
    selector: 'app-league-diamond-icon',
    template: `
        @if (league() === 'Diamond') {
            <svg [class]="class()" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 26 26" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M13 4.3094L6.33975 8.1547V15.8453L13 19.6906L19.6603 15.8453V8.1547L13 4.3094ZM21.6603 7L13 2L4.33975 7V17L13 22L21.6603 17V7Z"
                    fill="hsl(195, 60%, 50%)"
                />
                <path d="M6.87564 3.8453L4.87564 2.6906L0.875641 5V9.6188L2.87564 10.7735V6.1547L6.87564 3.8453Z" fill="hsl(195, 60%, 50%)" />
                <path d="M9 21.3812L9 23.6906L13 26L17 23.6906V21.3812L13 23.6906L9 21.3812Z" fill="hsl(195, 60%, 50%)" />
                <path d="M23.1244 10.7735V6.1547L19.1244 3.8453L21.1244 2.6906L25.1244 5V9.61881L23.1244 10.7735Z" fill="hsl(195, 60%, 50%)" />
                <path d="M10.5371 7.42196L13 8.84392L15.4629 7.42196L17.4629 8.57666L13 11.1533L8.53708 8.57666L10.5371 7.42196Z" fill="hsl(195, 60%, 50%)" />
                <path d="M7.80383 11.6533V9.34392L13 12.3439L18.1961 9.34392V11.6533L13 14.6533L7.80383 11.6533Z" fill="hsl(195, 60%, 50%)" />
                <path d="M18.1961 12.8439V15L13 18L7.80383 15V12.8439L13 15.8439L18.1961 12.8439Z" fill="hsl(195, 60%, 50%)" />
            </svg>
        }
    `,
})
export class LeagueDiamondIconComponent {
    class = input<string>('');
    league = input<string>('');
    size = signal('50');

    constructor() {
        effect(() => {
            this.size.set(this.class().includes('small-icon') ? '30' : '50');
        });
    }
}
