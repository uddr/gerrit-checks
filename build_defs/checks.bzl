def checks_java_library(
        name,
        gerrit_deps = [],
        **kwargs):
    """Creates a java_library where core deps are not included in the jar.

    Usually, Gerrit plugins are restricted to the libraries exported explicitly
    in the plugin API. For in-tree plugins, adding an additional dep on some
    other part of Gerrit (including from //lib) would cause that dep to get
    compiled into the plugin jar. We don't want that, so this macro takes care
    of excluding all specified gerrit_deps from the plugin jar.

    Dependencies from outside Gerrit core, such as from the plugin itself, may
    be supplied as regular deps.

    Args:
      name: name of the resulting java_library.
      gerrit_deps: dependencies from within Gerrit core, which should not be
          linked into the final plugin jar.
      **kwargs: additional arguments for the resulting java_library.
    """
    deps_lib = "__%s_deps_neverlink" % name
    native.java_library(
        name = deps_lib,
        neverlink = 1,
        visibility = ["//visibility:private"],
        exports = gerrit_deps,
    )

    native.java_library(
        name = name,
        deps = [":" + deps_lib],
        **kwargs
    )
