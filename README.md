# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

This plugin uses [polymer-cli](https://www.polymer-project.org/1.0/docs/tools/polymer-cli#install) to test.

After `bower install`, running `polymer test -l chrome` will run all tests in Chrome, and running `polymer serve`
and navigating to http://127.0.0.1:8081/components/checks/gr-checks/gr-checks-view_test.html allows for manual debugging.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html
