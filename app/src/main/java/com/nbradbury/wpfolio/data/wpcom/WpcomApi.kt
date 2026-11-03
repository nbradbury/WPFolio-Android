package com.nbradbury.wpfolio.data.wpcom

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
        @Query("number") number: Int,
        @Query("meta") meta: String = "site,feed",
    ): JsonObject

    @GET("rest/v1.2/read/following")
    suspend fun following(
        @Query("number") number: Int,
        @Query("page_handle") pageHandle: String?,
        @Query("before") before: String?,
        @Query("fields") fields: String = POST_FIELDS,
    ): JsonObject

    /** The user's liked posts, most recently liked first; [before] is a like date to page from. */
    @GET("rest/v1.2/read/liked")
    suspend fun liked(
        @Query("number") number: Int,
        @Query("before") before: String?,
        @Query("fields") fields: String = POST_FIELDS,
    ): JsonObject

    /** Sites to follow: based on the user's likes when signed in, a general mix when not. */
    @GET("rest/v1.2/read/recommendations/sites")
    suspend fun recommendedSites(
        @Query("number") number: Int,
        @Query("posts_per_site") postsPerSite: Int = 0,
    ): JsonObject

    @GET("rest/v1.2/read/sites/{blogId}/posts")
    suspend fun sitePosts(
        @Path("blogId") blogId: Long,
        @Query("number") number: Int = 20,
        @Query("fields") fields: String = POST_FIELDS,
    ): JsonObject

    @GET("rest/v1.2/read/feed/{feedId}/posts")
    suspend fun feedPosts(
        @Path("feedId") feedId: Long,
        @Query("number") number: Int = 20,
        @Query("fields") fields: String = POST_FIELDS,
    ): JsonObject

    /** Reader site search; works signed out too. */
    @GET("rest/v1.1/read/feed/")
    suspend fun searchFeeds(
        @Query("q") query: String,
        @Query("number") number: Int = 20,
        @Query("sort") sort: String = "relevance",
        @Query("meta") meta: String = "site",
    ): JsonObject

    /** VideoPress metadata (poster, size); public videos work signed out. */
    @GET("rest/v1.1/videos/{guid}")
    suspend fun video(@Path("guid") guid: String): JsonObject

    @POST("rest/v1.1/read/following/mine/new")
    suspend fun follow(@Query("url") url: String, @Query("source") source: String = "wpfolio"): JsonObject

    @POST("rest/v1.1/read/following/mine/delete")
    suspend fun unfollow(@Query("url") url: String): JsonObject

    @POST("rest/v1.1/sites/{blogId}/posts/{postId}/likes/new")
    suspend fun like(@Path("blogId") blogId: Long, @Path("postId") postId: Long): JsonObject

    @POST("rest/v1.1/sites/{blogId}/posts/{postId}/likes/mine/delete")
    suspend fun unlike(@Path("blogId") blogId: Long, @Path("postId") postId: Long): JsonObject

    companion object {
        const val BASE_URL = "https://public-api.wordpress.com/"

        /**
         * The post fields WpcomMapper reads. Without this, each post also carries its tags, terms,
         * attachments and metadata, about 60% of the response for 20 posts.
         */
        const val POST_FIELDS = "ID,site_ID,feed_ID,feed_item_ID,is_external,title,URL,date,excerpt,content," +
            "author,featured_image,featured_media,post_thumbnail,word_count,i_like,date_liked,site_name,like_count," +
            "discussion"
    }
}
