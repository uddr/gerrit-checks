# Fork notice

Fork of https://gerrit.googlesource.com/plugins/checks/
Some integraitions with specific CI tool

# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html

## Build in local (Ubuntu 24.04, Gerrit 3.14.x)

Gerrit 3.14 builds with Bazel 8.6.0 (pinned in the Gerrit tree's
`.bazelversion`) and Java 21. Use `bazelisk`, it picks the pinned Bazel
version up automatically.

    apt update
    apt install -y ca-certificates curl git python3 unzip zip openjdk-21-jdk
    curl -fsSLo /usr/local/bin/bazelisk \
      https://github.com/bazelbuild/bazelisk/releases/latest/download/bazelisk-linux-amd64
    chmod +x /usr/local/bin/bazelisk
    ln -sf /usr/local/bin/bazelisk /usr/local/bin/bazel

    git clone https://gerrit.googlesource.com/gerrit
    cd gerrit/
    git checkout v3.14.2
    git submodule update -f --init --recursive
    cd plugins/
    git clone https://github.com/uddr/gerrit-checks.git checks
    cd checks
    git checkout stable-3.14
    cd ../..
    bazel build plugins/checks

The plugin jar is written to `bazel-bin/plugins/checks/checks.jar`.

Notes compared to the 3.11 build:

* No `--javacopt="-source 17 -target 17"` is needed. Gerrit's `.bazelrc`
  already selects `remotejdk_21` for the language level and the toolchains.
* No `gerrit_uddr.patch` is needed. Gerrit 3.14 has no `WORKSPACE` file any
  more; it is built with bzlmod (`MODULE.bazel` / `WORKSPACE.bzlmod`) and the
  error-prone toolchains are registered from `MODULE.bazel` via
  `register_toolchains("//tools:all")` for Java 17 and 21.
* The build downloads the `bazlets` repository from
  `https://gerrit.googlesource.com/bazlets` (pinned in
  `tools/bazlets.MODULE.bazel`), so the build host needs network access.

## Enable e-mail notifications

To enable sending email notifications for "checks" status updates, you'll need to create the email
templates in `<your-site-path>/etc/mail`. In the simplest form, simply rename the example templates:

    cd "<your-site-path>"
    mv etc/mail/CombinedCheckStateUpdated.soy{.example,}
    mv etc/mail/CombinedCheckStateUpdatedHtml.soy{.example,}

## JavaScript Plugin

For running unit tests execute:

    bazel test --test_output=all //plugins/checks/web:web_test_runner

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
