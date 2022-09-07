FROM sphinxdoc/sphinx

WORKDIR /docs
# create non-root user to be able to edit the generated files without root access later
RUN groupadd -g 1000 sphinx-user \
    && useradd -ms /bin/bash -u 1000 -g 1000 sphinx-user \
    && chown 1000:1000 /docs
ADD requirements.txt /docs
RUN pip3 install -r requirements.txt

USER sphinx-user
EXPOSE 8000
# use the autobuild as default command
CMD exec sphinx-autobuild --port 8000 --host 0.0.0.0 -b html /docs /docs/_build/html
