package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load("//tools/bzl:js.bzl", "bundle_assets", "polygerrit_plugin")

polygerrit_plugin(
    name = "build_results",
    app = "build-results.html",
    plugin_name = "build_results",
    externs = ["externs.js"],
    srcs = glob([
            "*.html",
            "*.js",
        ]),
)
