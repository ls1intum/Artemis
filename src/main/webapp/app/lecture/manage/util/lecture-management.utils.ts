export function isFirstAfterOrEqualSecond(firstDate?: Date, secondDate?: Date): boolean {
    if (!firstDate || !secondDate) {
        return false;
    }
    return firstDate.getTime() >= secondDate.getTime();
}

export function addOneMinuteTo(referenceDate?: Date) {
    if (!referenceDate) {
        return undefined;
    }
    const minimumDate = new Date(referenceDate.getTime());
    minimumDate.setMinutes(minimumDate.getMinutes() + 1);
    return minimumDate;
}
