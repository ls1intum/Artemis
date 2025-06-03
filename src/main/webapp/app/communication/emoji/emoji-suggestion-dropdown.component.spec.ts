import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmojiSuggestionDropdownComponent, getEmojiSuggestions } from './emoji-suggestion-dropdown.component';

describe('EmojiSuggestionDropdownComponent', () => {
    let component: EmojiSuggestionDropdownComponent;
    let fixture: ComponentFixture<EmojiSuggestionDropdownComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EmojiSuggestionDropdownComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(EmojiSuggestionDropdownComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});

describe('getEmojiSuggestions', () => {
    it('should return empty array for empty query', () => {
        const result = getEmojiSuggestions('');
        expect(result).toEqual([]);
    });

    it('should return empty array for undefined query', () => {
        const result = getEmojiSuggestions(undefined as any);
        expect(result).toEqual([]);
    });

    it('should return emoji suggestions for valid queries', () => {
        const result = getEmojiSuggestions('smile');
        expect(result.length).toBeGreaterThan(0);
        expect(result.length).toBeLessThanOrEqual(3); // default max is 3

        // Check structure of results
        result.forEach((item) => {
            expect(item).toHaveProperty('name');
            expect(item).toHaveProperty('emoji');
            expect(typeof item.name).toBe('string');
            expect(typeof item.emoji).toBe('string');
            expect(item.name.length).toBeGreaterThan(0);
            expect(item.emoji.length).toBeGreaterThan(0);
        });
    });

    it('should respect max parameter', () => {
        const result = getEmojiSuggestions('heart', 1);
        expect(result.length).toBeLessThanOrEqual(1);
    });

    it('should find common emojis', () => {
        const testQueries = ['heart', 'smile', 'rocket', 'fire', 'star'];

        testQueries.forEach((query) => {
            const result = getEmojiSuggestions(query);
            expect(result.length).toBeGreaterThan(0);
        });
    });
});
