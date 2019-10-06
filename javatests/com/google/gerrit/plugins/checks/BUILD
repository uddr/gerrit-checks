package(default_visibility = ["//plugins/checks:visibility"])

load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "checks_tests",
    srcs = glob(["*.java"]),
    runtime_deps = ["//java/com/google/gerrit/lucene"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins/checks:checks__plugin",
    ],
)
