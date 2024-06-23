import { math } from 'exercise';

describe('math', () => {
    it('should add 1 and 2 to 3', () => {
        expect(math.add(1, 2)).toBe(3);
    });

    it('should multiply 3 and 5 to 15', () => {
        expect(math.multiply(3, 5)).toBe(15);
    });
});
