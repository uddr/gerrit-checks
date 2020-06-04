load("//tools/bzl:junit.bzl", "junit_tests")

package(default_visibility = ["//plugins/checks:visibility"])

junit_tests(
    name = "checks_tests",
    srcs = glob(["*.java"]),
    runtime_deps = ["//java/com/google/gerrit/lucene"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins/checks:checks__plugin",
    ],
)
