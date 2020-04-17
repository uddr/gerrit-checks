# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html

## UI tests

To run UI tests here will need install dependencies from both npm and bower.

`npm run wct-test` should take care both for you, read more in `package.json`.

You will need `polymer-bridges` which is a submodule you can clone from: https://gerrit-review.googlesource.com/admin/repos/polymer-bridges

## Test plugin on Gerrit

1. Build the bundle locally with: `bazel build gr-checks:gr-checks`
2. Serve your generated 'checks.js' somewhere, you can put it under `gerrit/plugins/checks/` folder and it will automatically served at `http://localhost:8081/plugins_/checks/`
3. Use FE dev helper, https://gerrit.googlesource.com/gerrit-fe-dev-helper/, inject the local served 'checks.js' to the page

If your plugin is already enabled, then you can block it and then inject the compiled local verison.

See more about how to use dev helper extension to help you test here: https://gerrit.googlesource.com/gerrit-fe-dev-helper/+/master