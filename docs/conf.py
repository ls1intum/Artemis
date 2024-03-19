# Configuration file for the Sphinx documentation builder.
#
# This file only contains a selection of the most common options. For a full
# list see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

# -- Path setup --------------------------------------------------------------

# If extensions (or modules to document with autodoc) are in another directory,
# add these directories to sys.path here. If the directory is relative to the
# documentation root, use os.path.abspath to make it absolute, like shown here.
#
# import os
# import sys
# sys.path.insert(0, os.path.abspath('.'))


# -- Project information -----------------------------------------------------

project = 'Artemis'
copyright = '2024, Technical University of Munich, Applied Software Engineering'
author = 'Technical University of Munich, Applied Software Engineering'


# -- General configuration ---------------------------------------------------

# The document name of the “main” document, that is, the document
# that contains the root toctree directive.
master_doc = "index"

# Add any Sphinx extension module names here, as strings. They can be
# extensions coming with Sphinx (named 'sphinx.ext.*') or your custom
# ones.
extensions = [
    "sphinx_rtd_theme",
    "sphinxcontrib.bibtex"
]

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
# This pattern also affects html_static_path and html_extra_path.
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store', 'venv']

linkcheck_ignore = [
    r'http(s)?://.*localhost(:\d+)?/?',
    r'https://artemis.cs.hm.edu/',  # DNS entry no longer exists
    r'https://bamboo.ase.in.tum.de/build/admin/edit/.*',
    r'https://hermes.artemis.cit.tum.de/',  # expired certificate
    # IEEE server returns code 418 when checking links
    r'https://doi.org/10.1109/CSEET58097.2023.00020',
    r'https://doi.org/10.1109/CSEET58097.2023.00021',
    r'https://doi.org/10.1109/CSEET58097.2023.00031',
    r'https://doi.org/10.1109/CSEET58097.2023.00037',
    r'https://doi.org/10.1109/ITHET50392.2021.9759809',
]
# when upgrading to Sphinx 7.1 or newer we can use
# `linkcheck_anchors_ignore_for_url` instead to exclude the angular.io and
# GitHub URLs completely instead of excluding specific anchors here
linkcheck_anchors_ignore = [
    # Angular guide
    r'deprecated-deep--and-ng-deep',
    r'testing-http-requests',
    r'no_errors_schema',
    r'stubbing-unneeded-components',
    # end Angular guide
    r'readme',  # links to GitHub readmes
    r'testing-of-pull-requests',  # Orion readme
    r'L[0-9]+(-L[0-9]+)?',  # links referring to concrete line numbers in the GitHub UI
    r'environment-variables',  # GitHub Spring wiki
    r'installation'  # k3d guide
]

# -- Publications ------------------------------------------------------------
bibtex_bibfiles = ['research/publications.bib']
bibtex_default_style = 'unsrtalpha'
bibtex_reference_style = 'label'

# -- Options for HTML output -------------------------------------------------

# The theme to use for HTML and HTML Help pages.  See the documentation for
# a list of builtin themes.
#
html_theme = 'sphinx_rtd_theme'
html_context = {
    "display_github": True,
    "github_user": "ls1intum",
    "github_repo": "Artemis",
    "github_version": "develop",
    "conf_py_path": "/docs/",
}
html_style = 'css/style.css'

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
html_static_path = ['_static']
