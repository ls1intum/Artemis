// Ported verbatim from shadcn-ui/cn (packages/cn/src/validators.ts, MIT),
// itself matching tailwind-merge 3.6.0's src/lib/validators.ts (MIT, Dany
// Castillo — see README.md). cn ships these only in marker form
// ({ $v: "isNumber" }), so the predicates are reproduced here and looked
// up by name.
const arbitraryValueRegex = /^\[(?:(\w[\w-]*):)?(.+)\]$/i;
const arbitraryVariableRegex = /^\((?:(\w[\w-]*):)?(.+)\)$/i;
const fractionRegex = /^\d+(?:\.\d+)?\/\d+(?:\.\d+)?$/;
const tshirtUnitRegex = /^(\d+(\.\d+)?)?(xs|sm|md|lg|xl)$/;
const lengthUnitRegex = /\d+(%|px|r?em|[sdl]?v([hwib]|min|max)|pt|pc|in|cm|mm|cap|ch|ex|r?lh|cq(w|h|i|b|min|max))|\b(calc|min|max|clamp)\(.+\)|^0$/;
const colorFunctionRegex = /^(rgba?|hsla?|hwb|(ok)?(lab|lch)|color-mix)\(.+\)$/;
const shadowRegex = /^(inset_)?-?((\d+)?\.?(\d+)[a-z]+|0)_-?((\d+)?\.?(\d+)[a-z]+|0)/;
const imageRegex = /^(url|image|image-set|cross-fade|element|(repeating-)?(linear|radial|conic)-gradient)\(.+\)$/;
export const isFraction = (v) => fractionRegex.test(v);
export const isNumber = (v) => !!v && !Number.isNaN(Number(v));
export const isInteger = (v) => !!v && Number.isInteger(Number(v));
export const isPercent = (v) => v.endsWith('%') && isNumber(v.slice(0, -1));
export const isTshirtSize = (v) => tshirtUnitRegex.test(v);
export const isAny = () => true;
const isLengthOnly = (v) => lengthUnitRegex.test(v) && !colorFunctionRegex.test(v);
const isNever = () => false;
const isShadow = (v) => shadowRegex.test(v);
const isImage = (v) => imageRegex.test(v);
export const isAnyNonArbitrary = (v) => !isArbitraryValue(v) && !isArbitraryVariable(v);
export const isNamedContainerQuery = (v) =>
    v.startsWith('@container') &&
    ((v[10] === '/' && v[11] !== undefined) ||
        (v[11] === 's' && v[16] !== undefined && v.startsWith('-size/', 10)) ||
        (v[11] === 'n' && v[18] !== undefined && v.startsWith('-normal/', 10)));
const getIsArbitraryValue = (value, testLabel, testValue) => {
    const result = arbitraryValueRegex.exec(value);
    if (result) {
        if (result[1]) return testLabel(result[1]);
        return testValue(result[2]);
    }
    return false;
};
const getIsArbitraryVariable = (value, testLabel, shouldMatchNoLabel = false) => {
    const result = arbitraryVariableRegex.exec(value);
    if (result) {
        if (result[1]) return testLabel(result[1]);
        return shouldMatchNoLabel;
    }
    return false;
};
const isLabelPosition = (l) => l === 'position' || l === 'percentage';
const isLabelImage = (l) => l === 'image' || l === 'url';
const isLabelSize = (l) => l === 'length' || l === 'size' || l === 'bg-size';
const isLabelLength = (l) => l === 'length';
const isLabelNumber = (l) => l === 'number';
const isLabelFamilyName = (l) => l === 'family-name';
const isLabelWeight = (l) => l === 'number' || l === 'weight';
const isLabelShadow = (l) => l === 'shadow';
export const isArbitrarySize = (v) => getIsArbitraryValue(v, isLabelSize, isNever);
export const isArbitraryValue = (v) => arbitraryValueRegex.test(v);
export const isArbitraryLength = (v) => getIsArbitraryValue(v, isLabelLength, isLengthOnly);
export const isArbitraryNumber = (v) => getIsArbitraryValue(v, isLabelNumber, isNumber);
export const isArbitraryWeight = (v) => getIsArbitraryValue(v, isLabelWeight, isAny);
export const isArbitraryFamilyName = (v) => getIsArbitraryValue(v, isLabelFamilyName, isNever);
export const isArbitraryPosition = (v) => getIsArbitraryValue(v, isLabelPosition, isNever);
export const isArbitraryImage = (v) => getIsArbitraryValue(v, isLabelImage, isImage);
export const isArbitraryShadow = (v) => getIsArbitraryValue(v, isLabelShadow, isShadow);
export const isArbitraryVariable = (v) => arbitraryVariableRegex.test(v);
export const isArbitraryVariableLength = (v) => getIsArbitraryVariable(v, isLabelLength);
export const isArbitraryVariableFamilyName = (v) => getIsArbitraryVariable(v, isLabelFamilyName);
export const isArbitraryVariablePosition = (v) => getIsArbitraryVariable(v, isLabelPosition);
export const isArbitraryVariableSize = (v) => getIsArbitraryVariable(v, isLabelSize);
export const isArbitraryVariableImage = (v) => getIsArbitraryVariable(v, isLabelImage);
export const isArbitraryVariableShadow = (v) => getIsArbitraryVariable(v, isLabelShadow, true);
export const isArbitraryVariableWeight = (v) => getIsArbitraryVariable(v, isLabelWeight, true);
