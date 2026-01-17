export interface IrisCitationDTO {
    index: number;
    type: 'video' | 'slide' | 'faq';
    link?: string;
    lectureName?: string;
    unitName?: string;
    faqQuestionTitle?: string;
    summary?: string;
    keyword?: string;
    page?: number;
    startTime?: string;
    endTime?: string;
    startTimeSeconds?: number;
    endTimeSeconds?: number;
}
