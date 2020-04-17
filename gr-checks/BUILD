package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")
load("//tools/bzl:js.bzl", "polygerrit_plugin")
load("//tools/bzl:genrule2.bzl", "genrule2")

polygerrit_plugin(
    name = "gr-checks",
    app = "checks-bundle.js",
    plugin_name = "checks",
)

rollup_bundle(
    name = "checks-bundle",
    srcs = glob(["*.js"]),
    entry_point = "gr-checks.js",
    rollup_bin = "//tools/node_tools:rollup-bin",
    sourcemap = "hidden",
    deps = [
        "@tools_npm//rollup-plugin-node-resolve",
    ],
)

genrule2(
    name = "gr-checks-static",
    srcs = [":gr-checks"],
    outs = ["gr-checks-static.jar"],
    cmd = " && ".join([
        "mkdir $$TMP/static",
        "cp -r $(locations :gr-checks) $$TMP/static",
        "cd $$TMP",
        "zip -Drq $$ROOT/$@ -g .",
    ]),
)
