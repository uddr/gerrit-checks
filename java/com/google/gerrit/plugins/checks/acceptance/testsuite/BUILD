load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/checks:visibility"],
)

java_library(
    name = "testsuite",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/server/util/time",
        "//plugins/checks:checks__plugin",
    ],
)
