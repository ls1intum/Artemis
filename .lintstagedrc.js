module.exports = {
    'src/{main/webapp,test}/**/*.{json,js,ts,css,scss,html}': ['prettier --write', 'eslint --fix'],
    'src/{main,test}/java/**/*.java': ['bash ./linting.sh'],
};
