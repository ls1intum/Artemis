module.exports = {
    'src/{main/webapp,test}/**/*.{json,js,ts,css,scss,html}': ['prettier --write'],
    'src/{main/webapp,test}/**/*.{ts,html}': ['eslint --fix'],
    'src/{main,test}/java/**/*.java': ['bash ./linting.sh'],
};
