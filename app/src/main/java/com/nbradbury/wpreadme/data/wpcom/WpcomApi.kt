package com.nbradbury.wpreadme.data.wpcom

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WpcomApi {
    @GET("rest/v1.1/me")
    suspend fun me(): JsonObject

    @GET("rest/v1.2/read/following/mine")
    suspend fun subscriptions(
        @Query("page") page: Int,
        @Query("number") number: Int = 200,
        @Query("meta") meta: String = "site,feed",
    ): JsonObject

    @GET("rest/v1.2/read/following")
    suspend fun following(
        @Query("number") number: Int,
        @Query("page_handle") pageHandle: String? = null,
        @Query("before") before: String? = null,
        @Query("meta") meta: String = "site",
    ): JsonObject

    @GET("rest/v1.2/read/sites/{blogId}/posts")
    suspend fun sitePosts(@Path("blogId") blogId: Long, @Query("number") number: Int = 20): JsonObject

    @GET("rest/v1.2/read/feed/{feedId}/posts")
    suspend fun feedPosts(@Path("feedId") feedId: Long, @Query("number") number: Int = 20): JsonObject

    @POST("rest/v1.1/read/following/mine/new")
    suspend fun follow(@Query("url") url: String, @Query("source") source: String = "wpreadme"): JsonObject

    @POST("rest/v1.1/read/following/mine/delete")
    suspend fun unfollow(@Query("url") url: String): JsonObject

    @POST("rest/v1.1/sites/{blogId}/posts/{postId}/likes/new")
    suspend fun like(@Path("blogId") blogId: Long, @Path("postId") postId: Long): JsonObject

    @POST("rest/v1.1/sites/{blogId}/posts/{postId}/likes/mine/delete")
    suspend fun unlike(@Path("blogId") blogId: Long, @Path("postId") postId: Long): JsonObject

    companion object {
        const val BASE_URL = "https://public-api.wordpress.com/"
    }
}
