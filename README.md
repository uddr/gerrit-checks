# Fork notice

Fork of https://gerrit.googlesource.com/plugins/checks/
Some integraitions with specific CI tool

# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html

## Build in local (Ubuntu 20.04)

    apt update
    apt install -y apt-transport-https curl gnupg vim openjdk-11-jdk zip
    curl -fsSL https://bazel.build/bazel-release.pub.gpg | gpg --dearmor >bazel-archive-keyring.gpg
    mv bazel-archive-keyring.gpg /usr/share/keyrings
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/bazel-archive-keyring.gpg] https://storage.googleapis.com/bazel-apt stable jdk1.8" | sudo tee /etc/apt/sources.list.d/bazel.list
    apt update
    apt install bazel-4.0.0
    git clone https://gerrit.googlesource.com/gerrit
    cd gerrit/
    git fetch
    git checkout v3.8.1
    git submodule update -f --init --recursive
    #bazel-4.0.0 clean --expunge
    #bazel-4.0.0 build gerrit
    cd plugins/
    git clone https://github.com/uddr/gerrit-checks.git checks
    cd checks
    git checkout stable-3.8
    cd ../..
    cp plugins/checks/gerrit_uddr.patch ./
    git apply gerrit_uddr.patch
    bazel-4.0.0 build --javacopt="-source 11 -target 11" plugins/checks

## Enable e-mail notifications

To enable sending email notifications for "checks" status updates, you'll need to create the email
templates in `<your-site-path>/etc/mail`. In the simplest form, simply rename the example templates:

    cd "<your-site-path>"
    mv etc/mail/CombinedCheckStateUpdated.soy{.example,}
    mv etc/mail/CombinedCheckStateUpdatedHtml.soy{.example,}

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
