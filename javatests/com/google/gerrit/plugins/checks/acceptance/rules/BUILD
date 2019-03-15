load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "checks_submit_rules",
    labels = [
        "checks_submit_rules",
    ],
    deps = [
        "//java/com/google/gerrit/git/testing",
        "//java/com/google/gerrit/server/util/time",
        "//plugins/checks:checks__plugin",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance/testsuite",
    ],
)
