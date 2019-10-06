load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "rules_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/mockito",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins/checks:checks__plugin",
    ],
)
