import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';

describe('EmojiUtils', () => {
    it('should return the correct emoji sheet url', () => {
        expect(EmojiUtils.EMOJI_SHEET_URL()).toBe(SERVER_API_URL + '/public/emoji/emoji_sheet_64_v6.0.1.png');
    });

    it.each(['1F519', '1F51A', '2714-FE0F', '2122-FE0F'])('should return the correct dark emoji url', (emojiId: string) => {
        expect(EmojiUtils.singleDarkModeEmojiUrlFn({ unified: emojiId } as EmojiData)).toBe(SERVER_API_URL + '/public/emoji/' + emojiId.toLowerCase() + '.png');
    });

    it.each([{ unified: 'foo' }, { unified: '' }, { unified: undefined }, undefined])('should return nothing for emojis that are not be replaced', (emoji: EmojiData) => {
        expect(EmojiUtils.singleDarkModeEmojiUrlFn(emoji)).toBe('');
    });
});
