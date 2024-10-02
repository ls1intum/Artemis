# Artemis Documentation

We use [Sphinx] for creating the Artemis documentation using [reStructuredText] (RST).
To get started with RST, check out the [Quickstart] or this [cheatsheet].

Please document the features that you have developed as **extensive as possible** from the user perspective, because the documentation also serves as a **user manual**. This is really important so that users can better understand how to use Artemis.

Creating a user manual for a learning platform such as Artemis can be a bit of a juggling act, especially when it's for students. Here are some best practices that should help:

## Best Practices
1. Artemis documentation must use **realistic examples** and personas and must avoid the use of test data. 

2. **Keep it simple and student friendly**: Remember, you're writing for students, not just fellow tech enthusiasts. Use plain language, avoid jargon, and explain technical terms when they can't be avoided.

3. **Use visual aids**: Screenshots, diagrams, and even short video tutorials can be a lot more effective than pages of text. They make it easier for students to understand and follow instructions

4. **Structure it intuitively**: Organize the content in a logical flow. Start with basic functions before moving to more advanced features. Think about how a student would use the system and structure your documentation accordingly.

5. **Include a searchable FAQ section**: Let's face it, not everyone is going to read the documentation cover-to-cover. A FAQ section for common issues or questions can be a lifesaver

6. **Apply accessible and inclusive design**: Make sure your documentation is accessible to all students, including those with disabilities. Use clear fonts, alt text for images, and consider a screen-reader-friendly version.

7. **Update regularly**: Artemis evolves, and so should the documentation. Keep it up-to-date with any changes in the system.

8. **Create a feedback loop**: Encourage students to give feedback on the documentation. They might point out confusing sections or missing information that you hadn't considered.

9. **Use familiar information**: This is crucial in the documentation because it simplifies the learning process for new users. Real-world scenarios demonstrate to users how to apply specific features within their own context, whereas test data can mislead and fails to reflect real use cases. Realistic examples and personas provide clarity and relevance, ensuring users can effectively understand and utilize Artemis.

10. **Use well defined personas**: Personas are vital for the development process, they do not only help readers to understand the documentation, but also allow developers to better understand Artemis and its users. Many organizations use personas, the two blog posts below contain additional introduction and motivation for the topic:
- [Using Personas During Design and Documentation](https://www.uxmatters.com/mt/archives/2010/10/using-personas-during-design-and-documentation.php)
- [Customer Personas: How to Write Them and Why You Need Them in Agile Software Development](https://community.atlassian.com/t5/App-Central/Customer-Personas-How-to-Write-Them-and-Why-You-Need-Them-in/ba-p/759228)

11. **Use realistic data**: Screenshots and screencasts included in Artemis documentation **must** present **realistic data**. That includes but is not limited to:
   - realistic user, course and exercise names
   - realistic text passages, like submissions contents and problem statements  

12. **Avoid test data**: Screenshots and screencasts included in Artemis documentation **must not** present any test data or server information. That includes but is not limited to:
   - `Test Server` and `Development` labels
   - test user, course and exercise names
   - _Lorem ipsum_ and mock text passages, like submissions contents and problem statements 
   - test server and `localhost` domains

13. **Keep screencasts short**: Cut them to at most two minutes to keep the documentation simple and easy to navigate. If you have larger ones, please split them into small screencasts based on the user workflow or features that you describe. Embed videos using TUM.Live and do **not** host them on Youtube (to avoid advertisement and data privacy issues).





## Documentation Hosting

[Read the Docs] (RtD) hosts the [Artemis documentation] for the `develop` (latest) branch, as well as for
git tags and branches of pull requests.
You can switch the shown version at the bottom of the sidebar.
The latest tag is always the _stable_ version.
For pull requests, the documentation is available at `https://artemis-platform--{PR_NUMBER}.org.readthedocs.build/en/{PR_NUMBER}/`.
RtD will build and deploy changes automatically.

## Installing Sphinx Locally

Optionally, create and activate a virtual environment:
```
python3 -m venv venv
```
On Linux or macOS:
```
source venv/bin/activate
```
On Windows (CMD):
```
venv\Scripts\activate.bat
```
On Windows (PowerShell):
```
venv\Scripts\Activate.ps1
```


[Sphinx] can run locally to generate the documentation in HTML and other formats.
You can install Sphinx using `pip` or choose a system-wide installation instead.
When using pip, consider using [Python virtual environments].
```bash
pip install -r requirements.txt
```
or
```bash
pip3 install -r requirements.txt
```
The [Installing Sphinx] documentation explains more install options.
For macOS, it is recommended to install it using homebrew:
```bash
brew install sphinx-doc
brew link sphinx-doc --force
pip3 install -r requirements.txt
```

## Running Sphinx Locally

To generate the documentation as a single HTML file, use the provided `Makefile`/`make.bat` files in the folder `docs`:
```bash
# maxOS / Linux
make singlehtml

# Windows
make.bat singlehtml
```


Using [sphinx-autobuild], the browser will live-reload on changes, ideal for viewing changes while writing documentation:
```bash
# maxOS / Linux
make livehtml

# Windows
make.bat livehtml
```

## Running Sphinx Locally with Docker

To generate the documentation as an HTML file, use the provided docker command from the project root:
```bash
docker run --rm -v ${PWD}/docs:/docs $(docker build -q -t sphinx -f docs/Dockerfile ./docs) make singlehtml
```

To auto-generate the documentation as HTML file and live-reload on changes,
use the provided docker command from the project root:
```bash
docker run --rm -it -v ${PWD}/docs:/docs -p 8000:8000 $(docker build -q -t sphinx -f docs/Dockerfile ./docs)
```

## Tool support
A list of useful tools to write documentation:
- [reStructuredText for Visual Studio Code](https://www.restructuredtext.net)
- [LanguageTool for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=adamvoss.vscode-languagetool): Provides offline grammar checking
- [ReStructuredText for IntelliJ](https://plugins.jetbrains.com/plugin/7124-restructuredtext)



<!-- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -->
[Artemis documentation]: https://artemis-platform.readthedocs.io

[reStructuredText]: https://docutils.sourceforge.io/rst.html
[Quickstart]: https://docutils.sourceforge.io/docs/user/rst/quickstart.html
[cheatsheet]: http://github.com/ralsina/rst-cheatsheet/raw/master/rst-cheatsheet.pdf

[Sphinx]: https://www.sphinx-doc.org/en/master/
[Installing Sphinx]: https://www.sphinx-doc.org/en/master/usage/installation.html
[Python virtual environments]: https://docs.python.org/3/library/venv.html
[sphinx-autobuild]: https://pypi.org/project/sphinx-autobuild/
[Read the Docs]: https://readthedocs.org


### Dependency management

Find outdated dependencies using the following command:
```
pip list --outdated
```

Find unused dependencies using the following command:
```
pip install deptry
deptry .
```
