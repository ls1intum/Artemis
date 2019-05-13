const cartesianProduct2ary = (a: any[], b: any[]) => {
    return a.reduce((acc, v) => [...acc, ...b.reduce((acc2, v2) => [...acc2, [...v, v2]], [])], []);
};

// TODO: Doesn't work for length >= 4;
export const cartesianProduct = (a: any[], b: any[], ...c: Array<any[]>) => {
    const prod = a.reduce((acc, v) => [...acc, ...b.reduce((acc2, v2) => [...acc2, [v, v2]], [])], []);
    if (c.length === 0) {
        return prod;
    } else if (c.length === 1) {
        return cartesianProduct2ary(prod, c[0]);
    } else {
        const [cHead, cTail] = c;
        return cartesianProduct(prod, cHead, cTail);
    }
};
