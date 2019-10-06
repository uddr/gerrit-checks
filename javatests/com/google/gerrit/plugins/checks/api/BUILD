load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "api_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//lib:guava",
        "//lib/truth",
        "//plugins/checks:checks__plugin",
    ],
)
