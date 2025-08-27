import { Component, input } from '@angular/core';

@Component({
    selector: 'app-league-silver-icon',
    template: `
        @if (league() === 'Silver') {
            <svg [class]="class()" width="40" height="40" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M12 4.3094L5.33975 8.1547V15.8453L12 19.6906L18.6603 15.8453V8.1547L12 4.3094ZM20.6603 7L12 2L3.33975 7V17L12 22L20.6603 17V7Z"
                    fill="hsl(0, 0%, 75%)"
                />
                <path d="M6.80385 11.3133V9.00391L12 12.0039L17.1962 9.00391V11.3133L12 14.3133L6.80385 11.3133Z" fill="hsl(0, 0%, 75%)" />
                <path d="M6.80385 14.8133V12.5039L12 15.5039L17.1962 12.5039V14.8133L12 17.8133L6.80385 14.8133Z" fill="hsl(0, 0%, 75%)" />
            </svg>
        }
    `,
})
export class LeagueSilverIconComponent {
    class = input<string>('');
    league = input<string>('');
}
