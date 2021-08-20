# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html

## JavaScript Plugin

For running unit tests execute:

    bazel test --test_output=all //plugins/checks/web:karma_test

For checking or fixing eslint formatter problems run:

    bazel test //plugins/checks/web:lint_test
    bazel run //plugins/checks/web:lint_bin -- --fix "$(pwd)/plugins/checks/web"

For testing the plugin with
[Gerrit FE Dev Helper](https://gerrit.googlesource.com/gerrit-fe-dev-helper/)
build the JavaScript bundle and copy it to the `plugins/` folder:

    bazel build //plugins/checks/web:checks
    cp -f bazel-bin/plugins/checks/web/checks.js plugins/

and let the Dev Helper redirect from `.+/plugins/checks/static/checks.js` to
`http://localhost:8081/plugins_/checks.js`.

