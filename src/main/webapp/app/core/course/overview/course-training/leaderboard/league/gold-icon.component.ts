import { Component, input } from '@angular/core';

@Component({
    selector: 'app-league-gold-icon',
    template: `
        @if (league() === 'Gold') {
            <svg [class]="class()" [attr.width]="getSize()" [attr.height]="getSize()" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M12 4.3094L5.33975 8.1547V15.8453L12 19.6906L18.6603 15.8453V8.1547L12 4.3094ZM20.6603 7L12 2L3.33975 7V17L12 22L20.6603 17V7Z"
                    fill="hsl(45, 100%, 50%)"
                />
                <path d="M9.5371 7.42196L12 8.84392L14.4629 7.42196L16.4629 8.57666L12 11.1533L7.5371 8.57666L9.5371 7.42196Z" fill="hsl(45, 100%, 50%)" />
                <path d="M6.80385 11.6533V9.34392L12 12.3439L17.1962 9.34392V11.6533L12 14.6533L6.80385 11.6533Z" fill="hsl(45, 100%, 50%)" />
                <path d="M17.1962 12.8439V15L12 18L6.80385 15V12.8439L12 15.8439L17.1962 12.8439Z" fill="hsl(45, 100%, 50%)" />
            </svg>
        }
    `,
})
export class LeagueGoldIconComponent {
    class = input<string>('');
    league = input<string>('');

    getSize(): string {
        return this.class().includes('small-icon') ? '30' : '50';
    }
}
