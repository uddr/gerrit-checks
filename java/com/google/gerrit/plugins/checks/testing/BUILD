load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/checks:visibility"],
)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/git/testing",
        "//java/com/google/gerrit/truth",
        "//lib:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins/checks:checks__plugin",
    ],
)
