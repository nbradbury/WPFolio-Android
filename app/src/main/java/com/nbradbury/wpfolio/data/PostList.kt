package com.nbradbury.wpfolio.data

import androidx.annotation.Keep

/** The list a post was opened from, which the article screen swipes through. */
@Keep // A navigation route argument, so R8 mustn't rename it.
enum class PostList { FEED, SITE, SAVED }
