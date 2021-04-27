load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)

package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

gerrit_plugin(
    name = "checks",
    srcs = glob(["java/com/google/gerrit/plugins/checks/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.HttpModule",
        "Gerrit-InitStep: com.google.gerrit.plugins.checks.Init",
    ],
    resource_jars = ["//plugins/checks/gr-checks:checks"],
    resource_strip_prefix = "plugins/checks/resources",
    resources = glob(["resources/**/*"]),
    deps = ["//plugins/checks/proto:cache_java_proto"],
)
