package dev.kettu.hyangsang.network

import dev.kettu.hyangsang.BuildConfig
import dev.kettu.hyangsang.parser.userAgent
import okhttp3.OkHttpClient

/** The User-Agent of every request the app makes: feeds, article pages and images. */
val USER_AGENT = userAgent(BuildConfig.VERSION_NAME)

/** Sends [USER_AGENT] instead of OkHttp's own. */
fun OkHttpClient.Builder.withUserAgent(): OkHttpClient.Builder = addInterceptor { chain ->
    chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
}
