package(default_visibility = ["//plugins/checks:visibility"])

proto_library(
    name = "cache_proto",
    srcs = ["cache.proto"],
)

java_proto_library(
    name = "cache_java_proto",
    visibility = ["//visibility:public"],
    deps = [":cache_proto"],
)
