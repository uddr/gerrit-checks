load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_checker",
    labels = [
        "api",
        "noci",
    ],
    deps = [
        "//java/com/google/gerrit/git/testing",
        "//java/com/google/gerrit/server/util/time",
        "//plugins/checks:checks__plugin",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance/testsuite",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/testing",
    ],
)
