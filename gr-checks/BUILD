package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load("//tools/bzl:js.bzl", "polygerrit_plugin")
load("//tools/bzl:genrule2.bzl", "genrule2")

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
