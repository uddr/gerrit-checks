package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "checks",
    srcs = glob(["java/com/google/gerrit/plugins/checks/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.api.HttpModule",
    ],
    deps = [":checks-deps-neverlink"],
)

java_library(
    name = "checks-deps-neverlink",
    neverlink = True,
    visibility = ["//visibility:private"],
    exports = [
        "//java/com/google/gerrit/server/api",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
