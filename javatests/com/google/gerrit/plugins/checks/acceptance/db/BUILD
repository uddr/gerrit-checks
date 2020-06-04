load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

package(default_visibility = ["//plugins/checks:visibility"])

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "get_combined_check_state",
    labels = [
        "get_combined_check_state",
    ],
    deps = [
        "//java/com/google/gerrit/git/testing",
        "//plugins/checks:checks__plugin",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance/testsuite",
    ],
)
