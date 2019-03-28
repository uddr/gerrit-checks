package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load("//tools/bzl:js.bzl", "bundle_assets", "polygerrit_plugin")

polygerrit_plugin(
    name = "gr-checks",
    app = "gr-checks.html",
    plugin_name = "checks-pg",
    externs = ["externs.js"],
    srcs = glob([
            "*.html",
            "*.js",
        ]),
)
