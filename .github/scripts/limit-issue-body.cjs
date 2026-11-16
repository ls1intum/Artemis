const fs = require('node:fs');

function limitIssueBody(body, runUrl) {
    // Leave room below GitHub's 65,536-character limit, including the full-report link.
    const maximumLength = 60000;
    if (body.length <= maximumLength) {
        return body;
    }
    const notice = `\n\nReport truncated to fit GitHub's issue-body limit. See the [full report in the workflow logs](${runUrl}).\n`;
    // JavaScript counts UTF-16 units. Avoid leaving half of an astral character at the cut.
    const prefix = body.slice(0, maximumLength - notice.length).replace(/[\uD800-\uDBFF]$/, '');
    return prefix + notice;
}

module.exports = { limitIssueBody };

if (require.main === module) {
    const [bodyPath, runUrl] = process.argv.slice(2);
    const body = fs.readFileSync(bodyPath, 'utf8');
    process.stdout.write(body);
    fs.writeFileSync(bodyPath, limitIssueBody(body, runUrl));
}
