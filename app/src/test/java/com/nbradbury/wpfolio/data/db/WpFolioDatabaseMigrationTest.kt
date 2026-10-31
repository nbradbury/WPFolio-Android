package com.nbradbury.wpfolio.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WpFolioDatabaseMigrationTest {

    @Test
    fun `upgrading from version 1 keeps posts and their likes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            createFromSchema(db, version = 1)
            db.execSQL(
                """
                INSERT INTO posts (id, siteId, siteName, title, excerpt, contentHtml, url, publishedAt, wordCount,
                    isRead, isSaved, isLiked)
                VALUES ('p1', 's1', 'Site', 'Liked before the upgrade', '', '', 'https://a.test/p1', 1000, 0, 1, 0, 1)
                """
            )
        }

        // Opening runs the auto-migration and fails if the result doesn't match version 2's schema.
        val db = Room.databaseBuilder(context, WpFolioDatabase::class.java, name).allowMainThreadQueries().build()
        val post = db.postDao().get("p1")!!
        assertEquals("Liked before the upgrade", post.title)
        assertTrue(post.isLiked)
        assertNull(post.likedAt)
        db.close()
    }

    /** Creates the tables an installed app at [version] has, from Room's exported schema. */
    private fun createFromSchema(db: SQLiteDatabase, version: Int) {
        val schema = File("schemas/${WpFolioDatabase::class.java.name}/$version.json").readText()
        val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
        for (entity in database.getValue("entities").jsonArray.map { it.jsonObject }) {
            val table = entity.getValue("tableName").jsonPrimitive.content
            val statements = listOf(entity.getValue("createSql")) +
                entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql") }
            statements.forEach { db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
    }
}
