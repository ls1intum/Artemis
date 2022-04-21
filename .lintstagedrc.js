module.exports = {
    '{,src/**/,webpack/}*.{md,json,yml,html,cjs,mjs,js,ts,tsx,css,scss}': ['prettier --write'],
    'src/{main,test}/java/**/*.java': ['bash ./linting.sh'],
};
