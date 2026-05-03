package dev.kettu.hyangsang.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface ArticleService {
    @GET
    suspend fun getArticle(@Url url: String): Response<ResponseBody>
}