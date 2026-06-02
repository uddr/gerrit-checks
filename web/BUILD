load("@aspect_rules_ts//ts:defs.bzl", "ts_config", "ts_project")
load("@com_googlesource_gerrit_bazlets//js:defs.bzl", "gerrit_js_bundle")
load("//tools/bzl:js.bzl", "web_test_runner")
load("//tools/js:eslint.bzl", "plugin_eslint")

package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    deps = [
        "//plugins:tsconfig-plugins-base",
    ],
)

ts_project(
    name = "checks-ts",
    srcs = glob(
        ["**/*.ts"],
        exclude = ["**/*test*"],
    ),
    incremental = True,
    out_dir = "_bazel_ts_out",
    tsconfig = ":tsconfig",
    deps = [
        "//plugins:node_modules",
    ],
)

ts_project(
    name = "checks-ts-tests",
    srcs = glob(["**/*.ts"]),
    incremental = True,
    out_dir = "_bazel_ts_out_tests",
    tsconfig = ":tsconfig",
    visibility = ["//visibility:public"],
    deps = [
        "//plugins:node_modules",
        "//polygerrit-ui:node_modules",
    ],
)

gerrit_js_bundle(
    name = "checks",
    srcs = [":checks-ts"],
    entry_point = "_bazel_ts_out/plugin.js",
)

# Creates lint_test and lint_bin rules.
plugin_eslint()

web_test_runner(
    name = "web_test_runner",
    srcs = ["web_test_runner.sh"],
    data = [
        ":checks-ts-tests",
        ":tsconfig",
        "//plugins:node_modules",
    ],
)
