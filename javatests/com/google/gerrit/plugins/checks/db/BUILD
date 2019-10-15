load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "db_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/truth",
        "//plugins/checks:checks__plugin",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/testing",
    ],
)
