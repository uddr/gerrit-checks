load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/checks:visibility"],
)

java_library(
    name = "acceptance",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//plugins/checks:checks__plugin",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance/testsuite",
    ],
)
