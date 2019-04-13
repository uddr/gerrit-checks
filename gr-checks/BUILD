package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load("//tools/bzl:js.bzl", "polygerrit_plugin")

polygerrit_plugin(
    name = "gr-checks",
    srcs = glob([
        "*.html",
        "*.js",
    ]),
    app = "gr-checks.html",
    externs = ["externs.js"],
    plugin_name = "checks-pg",
)
