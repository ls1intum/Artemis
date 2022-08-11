**************
Client Theming
**************

Artemis ships with two themes: Light Mode and Dark Mode. It is important to ensure that all UI components look
consistent and good in both themes. Therefore, please follow the rules and hints below.

In general, keep in mind: **All UI changes need to be verified in both themes!**

0. Color Usage
==============

We use different colors for our UI elements in both themes. These colors are passed into Bootstrap, so if you use
default components such as buttons, cards, forms, etc., they will be automagically themed correctly.

For your custom components and custom stylesheets, please follow this strict global rule:

.. raw:: html

    <style> .bigred { color: red; font-size: 18px; font-weight: bold; } </style>

.. role:: bigred

:bigred:`Do not hard code any color values in your component stylesheets, templates or TypeScript files!`

Most likely, any colors hard-coded in your component stylesheets will look bad in either the light mode or dark mode.
So, you either need to specify different colors for both themes, or you could just use default colors, which is preferred.

.. WARNING::
    - **Pull Requests with hard-coded colors in component files will not be merged.**
    - We want to avoid further color fragmentation in the future.
      You need a good justification to not use default or already provided colors, be it derived or completely custom ones.

Please check your available options in this order:

1. Use global default colors and Bootstrap classes *(preferred)*
    For most use-cases, using one of the pre-provided colors is the way to go. Really think deeply whether you need
    a custom color.

    Check out the top of ``_default_variables.scss`` to see the available default colors. While you should not
    use ``black, gray-XXX, white`` in your components as that would equal a hard-coded, not theme-aware color, you should re-use signal colors, base colors and pre-provided 'colorful' colors.

    All variables in this file are globally available in the application as native CSS variables.
    For example, ``$danger`` can be accessed in all SCSS files using ``var(--danger)``.

    .. list-table:: Important default and signal colors
        :widths: 10, 50, 20
        :header-rows: 1

        * - Color
          - Use case
          - Usage
        * - ``bs-body-color``
          - The default font color; will be black in light mode and white in dark mode.
          - CSS: ``var(--bs-body-color)``
        * - ``bs-body-bg``
          - The body background color; will be something bright in light mode and something darker in dark mode.

            It can be used for smaller boxes in main content, as it's usually pretty distinguishable from the
            background color used in Artemis' primary content area.
          - CSS: ``var(--bs-body-bg)``
        * - ``artemis-dark``
          - A dark color that is typical for some of Artemis' UI elements, for example the navbar background.
          - CSS: ``var(--artemis-dark)``
        * - ``primary``
          - A blue-ish default color to indicate a primary action. Also used as link color. Use this to indicate
            the primary next step for the user (or one of them).
          - CSS: ``var(--primary)``

            For text: ``<span class="text-primary">``

            For buttons: ``<button class="btn btn-primary">``
        * - ``secondary``
          - A gray color. Use this for secondary action buttons and hint texts.
          - CSS: ``var(--secondary)``

            For text: ``<span class="text-secondary">``

            For buttons: ``<button class="btn btn-secondary">``
        * - ``success``
          - A green color indicating a successful operation, state, or safe action.
          - CSS: ``var(--success)``

            For text: ``<span class="text-success">``

            For buttons: ``<button class="btn btn-success">``
        * - ``danger``
          - A red color indicating a failed operation, error-state, or dangerous action.
          - CSS: ``var(--danger)``

            For text: ``<span class="text-danger">``

            For buttons: ``<button class="btn btn-danger">``
        * - ``warning``
          - An orange color indicating a partly failed operation, a warning, or an unsafe, yet not ultra-dangerous action.
          - CSS: ``var(--warning)``

            For text: ``<span class="text-warning">``

            For buttons: ``<button class="btn btn-warning">``
        * - ``info``
          - An teal-ish color indicating an informational element.
          - CSS: ``var(--info)``

            For text: ``<span class="text-info">``

            For buttons: ``<button class="btn btn-info">``

    There are more theme-aware colors to choose from; please see the variables file.

    If you need to design entire boxes using one of the signal colors, you should use alert boxes.

    Either add one of the Boostrap alert classes to your box, such as ``alert alert-danger``, or use our globally
    defined colors:

    .. code-block:: scss

        var(--artemis-alert-XXXX-color); // The text color of the alert
        var(--artemis-alert-XXXX-background); // The less intensive background color of the alert
        var(--artemis-alert-XXX-border); // The border color of the alert

    with ``XXX`` being one of: ``info, danger, warning, success, neutral``.

    If you need to separate something from the background, try to use the ``bg-light`` class which should work in both themes.

2. **Define your own colors for each theme**

    | If the options above don't suit your use case, you can define your own color variables.
    | These colors must be theme-aware, so you have to select a good color for both themes and add them to each
      theme's stylesheet.

    .. TIP::
        | Artemis uses ``white`` in light mode and ``$neutral-dark`` in dark mode
          as background for the main content area, cards, etc.
        |
        | For ``$neutral-dark``, a few lightened default options exist: ``$neutral-dark-l-5;`` to ``$neutral-dark-l-20;`` in steps of 5.
        | Therefore, if you need to separate something from the background, choose one of ``gray-XXX`` for light mode and a lightened option of ``$neutral-dark`` in dark mode.
        |
        | Keep this in mind while you select the colors to use.

    Let's go through it step by step. Let's say, you want to give a box a special background color.

    1. Define a class for it in your component's SCSS file, and use a new, unique variable name as value:

        .. code-block:: scss

            .my-box {
                background-color: var(--my-special-component-my-box-background-color);
            }

    2. Add the variable as SCSS variable to both theme variable sheets (``_default-variables.scss`` and ``_dark-variables.scss``):

        .. code-block:: scss

            // My Special Component
            $my-special-component-my-box-background-color: [...];

    3. Select a value for each theme.

        | **Re-use preset colors where possible!** For example, choose ``gray-700`` in light mode and ``gray-400`` in dark mode.
        | This is still a good approach as you're reusing already provided colors.

        .. code-block:: scss

            // My Special Component
            $my-special-component-my-box-background-color: $gray-600;

        .. IMPORTANT::
            The two options below are meant as a fallback for special cases.
            Please justify the use of these options in your PR description.

        If you need a **slightly modified preset color**, use `SCSS functions <https://sass-lang.com/documentation/modules/color>`_ to derive it.

        .. code-block:: scss

            // My Special Component
            $my-special-component-my-box-background-color: darken($success, 20);

        **If all of this doesn't work, define your own colors.**

        .. code-block:: scss

            // My Special Component
            $my-special-component-my-box-background-color: #123456;

1. Theme-specific global styles
===============================

It might happen that you need to modify a global style rule in one of the themes, for example if you're using an external library which styles need to be overridden.

| Each theme has its own file to which custom global styles can be added: ``theme-dark.scss`` and ``theme-default.scss``.
| For styles that should be applied in both themes, use ``global.scss``.

2. Theme Service
================

There will be occasions where you need to know in your components which theme is currently applied.
The ``ThemeService`` will provide this information where needed.
For example, you could add a reactive flag to your component that indicates whether or not the current theme is dark:

.. code-block:: ts

    @Component({
        selector: 'jhi-my-component',
        templateUrl: './my-component.component.html',
        styleUrls: ['my-component.component.scss'],
    })
    export class MyComponent implements OnInit, OnDestroy {
        isDark = false;
        themeSubscription: Subscription;

        constructor(private themeService: ThemeService) {}

        ngOnInit() {
            this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => {
                this.isDark = theme === Theme.DARK;
            });
        }

        ngOnDestroy() {
           this.themeSubscription.unsubscribe();
        }
    }

| From there, you can do whatever you need to do to change the behavior of your component based on the theme.
| Alternatively, you can execute any actions directly in the ``subscribe()`` block.
  **The service will fire an event containing the current theme immediately as soon as you subscribe**, and one more
  event for each theme change from then on util you unsubscribe.
| You can get the current theme using ``themeService.getCurrentTheme()`` at any time.


Additionally, it's possible to change the theme programmatically. **However, this should be rare**: Usually, the user decides which theme
they want to use by using the theme switching component in the navbar. Any use of this must therefore be justified and
survive a detailed review.

Example:

.. code-block:: ts

    this.themeService.applyThemeExplicitly(Theme.DARK);



