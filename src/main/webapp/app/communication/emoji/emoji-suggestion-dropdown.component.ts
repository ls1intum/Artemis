import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { EmojiSearch } from '@ctrl/ngx-emoji-mart';
import { EmojiData, EmojiService } from '@ctrl/ngx-emoji-mart/ngx-emoji';

// Cache instances at module level for better performance
const emojiService = new EmojiService();
const emojiSearch = new EmojiSearch(emojiService);

@Component({
    selector: 'jhi-emoji-suggestion-dropdown',
    templateUrl: './emoji-suggestion-dropdown.component.html',
    styleUrls: ['./emoji-suggestion-dropdown.component.scss'],
    standalone: true,
    imports: [NgClass, NgStyle],
    providers: [EmojiSearch, EmojiService],
})
export class EmojiSuggestionDropdownComponent {
    @Input() suggestions: { name: string; emoji: string }[] = [];
    @Input() dropdownStyle: { [key: string]: string } = {};
    @Output() onSelect = new EventEmitter<{ name: string; emoji: string }>();
    @Input() activeIndex: number = 0;

    constructor() {}
}

/**
 * Gets emoji suggestions using the emoji mart search functionality.
 * This provides access to the full emoji database instead of a limited manual list.
 *
 * @param query The search query (e.g., "joy", "heart", "rocket")
 * @param max Maximum number of results to return (default: 3)
 * @returns Array of emoji suggestions with name and emoji properties
 */
export function getEmojiSuggestions(query: string, max: number = 3): { name: string; emoji: string }[] {
    if (!query) return [];

    // Search using the cached emoji search instance
    // This gives us access to the full emoji database
    const results = emojiSearch.search(query, undefined, max);

    if (!results || results.length === 0) {
        return [];
    }

    // Convert EmojiData objects to our expected format
    return results
        .map((emojiData: EmojiData) => ({
            name: emojiData.id || emojiData.colons?.replace(/:/g, '') || '',
            emoji: emojiData.native || emojiData.emoticons?.[0] || '',
        }))
        .filter((item) => item.name && item.emoji);
}
