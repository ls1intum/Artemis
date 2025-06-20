import { Component, input, output } from '@angular/core';
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
    suggestions = input<{ name: string; emoji: string }[]>([]);
    dropdownStyle = input<{ [key: string]: string }>({});
    activeIndex = input<number>(0);
    activeIndexChange = output<number>();
    isKeyboardNavigation = false;

    onSelect = output<{ name: string; emoji: string }>();

    constructor() {}

    /**
     * Set keyboard navigation mode when active index changes via keyboard
     */
    setKeyboardNavigation() {
        this.isKeyboardNavigation = true;
    }

    /**
     * Reset keyboard navigation mode when mouse moves
     */
    resetKeyboardNavigation() {
        this.isKeyboardNavigation = false;
    }
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
