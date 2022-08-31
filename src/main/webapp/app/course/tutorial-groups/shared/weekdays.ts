export const weekDays = [
    {
        id: 'monday',
        translationKey: 'monday',
        value: 1,
    },
    {
        id: 'tuesday',
        translationKey: 'tuesday',
        value: 2,
    },
    {
        id: 'wednesday',
        translationKey: 'wednesday',
        value: 3,
    },
    {
        id: 'thursday',
        translationKey: 'thursday',
        value: 4,
    },
    {
        id: 'friday',
        translationKey: 'friday',
        value: 5,
    },
    {
        id: 'saturday',
        translationKey: 'saturday',
        value: 6,
    },
    {
        id: 'sunday',
        translationKey: 'sunday',
        value: 7,
    },
];

export function getDayTranslationKey(dayOfWeek?: number) {
    if (!dayOfWeek) {
        return '';
    } else {
        return `artemisApp.scheduleForm.weekDay.${weekDays[dayOfWeek - 1].translationKey}`;
    }
}
