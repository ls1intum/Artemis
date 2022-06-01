import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

const EMOJI_URL = SERVER_API_URL + '/public/emoji/';

const EMOJIS_TO_REPLACE = [
    '1F519', // back
    '1F51A', // end
    '1F51B', // on
    '1F51C', // soon
    '1F51D', // top
    '2716-FE0F', // heavy_multiplication_x
    '2795', // heavy_plus_sign
    '2796', // heavy_minus_sign
    '2797', // heavy_division_sign
    '1F4B2', // heavy_dollar_sign
    '2714-FE0F', // heavy_check_mark
    '2122-FE0F', // tm
    '00A9-FE0F', // copyright
    '00AE-FE0F', // registered
];

export class EmojiUtils {
    public static readonly EMOJI_SHEET_URL = () => EMOJI_URL + 'emoji_sheet_64.png';

    public static readonly singleDarkModeEmojiUrlFn: (emoji: EmojiData | null) => string = (emoji: EmojiData) => {
        if (emoji.unified && EMOJIS_TO_REPLACE.includes(emoji.unified)) {
            return EMOJI_URL + emoji.unified.toLowerCase() + '.png';
        }
        return '';
    };
}
