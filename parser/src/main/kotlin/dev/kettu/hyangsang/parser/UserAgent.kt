package dev.kettu.hyangsang.parser

/**
 * The User-Agent for requests to the sites articles come from. It names the app and links to
 * the project, so sites can tell what the requests are and where to find out more. [version] is
 * the app's version, or what else makes the requests, such as the parser canary.
 */
fun userAgent(version: String): String = "Hyangsang/$version (+https://github.com/Gawkat/hyangsang)"
