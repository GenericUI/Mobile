package com.nexustools.genericui.android

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import android.os.Looper
import com.nexustools.genericui.jvm.JvmDatabaseBase
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabasePreparedSql
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseQuerySqlTranslator
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema
import com.nexustools.genericui.util.db.DatabaseValue
import java.io.File

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl]
 * extending [JvmDatabaseBase] with [SQLiteOpenHelper] and `android.database.sqlite.SQLiteDatabase`,
 * executing queries via parameterized [DatabaseQuerySqlTranslator] statements on `Dispatchers.IO` and
 * falling back transparently to [JvmDatabaseBase] in-memory storage during headless JVM host tests.
 */
class AndroidDatabaseNativeImpl(
    databaseName: String = "GenericUI",
    filePath: String = "GenericUI.db",
    version: Int = 1,
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : JvmDatabaseBase(
    databaseName = databaseName,
    filePath = filePath,
    version = version,
    isOnMainThread = {
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    }
) {
    private var openHelper: AndroidSqliteOpenHelper? = null
    private var sqliteDatabase: SQLiteDatabase? = null
    private var sqliteUnavailable: Boolean = false
    private val verifiedSchemaTables = HashSet<String>()

    override suspend fun defineTable(schema: DatabaseTableSchema): Boolean {
        if (isDisposed) return false
        super.defineTable(schema)
        val norm = normalizeTableName(schema.tableName)
        val colMap = registerSchemaColumns(norm, schema)
        return runOnDatabaseIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnDatabaseIo true
                if (norm !in verifiedSchemaTables) {
                    for (stmt in DatabaseQuerySqlTranslator.buildCreateTableSql(schema)) {
                        executePreparedStatement(db, stmt)
                    }
                    val existingSanitized = querySqliteTableColumns(db, norm).keys.mapTo(HashSet()) {
                        DatabaseQuerySqlTranslator.sanitizeIdentifier(it).lowercase()
                    }
                    val missingCols = colMap.filterKeys { colName ->
                        DatabaseQuerySqlTranslator.sanitizeIdentifier(colName).lowercase() !in existingSanitized
                    }
                    if (missingCols.isNotEmpty()) {
                        ensureRecordColumnsExist(db, norm, missingCols)
                    }
                    verifiedSchemaTables.add(norm)
                }
                true
            }
        }
    }

    override suspend fun putRecord(table: String, record: DatabaseRecord): Boolean {
        if (isDisposed) return false
        super.putRecord(table, record)
        val norm = normalizeTableName(table)
        return runOnDatabaseIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnDatabaseIo true
                val (pkCol, enrichedRecord) = enrichRecordForUpsert(norm, record)
                ensureTableAndColumnsForRecord(db, norm, pkCol, enrichedRecord)
                val upsertSql = DatabaseQuerySqlTranslator.buildUpsertRecordSql(
                    table = norm,
                    record = enrichedRecord,
                    primaryKeyColumn = pkCol
                )
                executePreparedInsert(db, upsertSql) >= 0L
            }
        }
    }

    override suspend fun query(query: DatabaseQuery): List<DatabaseRecord> {
        if (isDisposed) return emptyList()
        val norm = normalizeTableName(query.table)
        return runOnDatabaseIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnDatabaseIo queryInMemoryFallback(query)
                if (!tableExistsInSqlite(db, norm)) {
                    return@runOnDatabaseIo emptyList()
                }
                val normalizedQuery = query.copy(table = norm)
                val prepared = DatabaseQuerySqlTranslator.buildSelectSql(normalizedQuery)
                executeSelectCursor(db, prepared, norm, query.selectedColumns)
            }
        }
    }

    override suspend fun count(query: DatabaseQuery): Long {
        if (isDisposed) return 0L
        val norm = normalizeTableName(query.table)
        return runOnDatabaseIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnDatabaseIo countInMemoryFallback(query)
                if (!tableExistsInSqlite(db, norm)) {
                    return@runOnDatabaseIo 0L
                }
                val normalizedQuery = query.copy(table = norm)
                val prepared = DatabaseQuerySqlTranslator.buildCountSql(normalizedQuery)
                db.compileStatement(prepared.sql).use { stmt ->
                    bindAll(stmt, prepared.bindValues)
                    stmt.simpleQueryForLong()
                }
            }
        }
    }

    override suspend fun deleteWhere(query: DatabaseQuery): Int {
        if (isDisposed) return 0
        val memDeleted = super.deleteWhere(query)
        val norm = normalizeTableName(query.table)
        return runOnDatabaseIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnDatabaseIo memDeleted
                if (!tableExistsInSqlite(db, norm)) {
                    return@runOnDatabaseIo 0
                }
                val normalizedQuery = query.copy(table = norm)
                val prepared = DatabaseQuerySqlTranslator.buildDeleteSql(normalizedQuery)
                executePreparedUpdateDelete(db, prepared)
            }
        }
    }

    override suspend fun dropTable(table: String): Boolean {
        if (isDisposed) return false
        val memDropped = super.dropTable(table)
        val norm = normalizeTableName(table)
        return runOnDatabaseIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnDatabaseIo memDropped
                val existed = tableExistsInSqlite(db, norm)
                val tableIdent = DatabaseQuerySqlTranslator.quoteIdentifier(norm)
                db.execSQL("DROP TABLE IF EXISTS $tableIdent")
                knownTableColumns.remove(norm)
                verifiedSchemaTables.remove(norm)
                existed || memDropped
            }
        }
    }

    override suspend fun tables(): List<String> {
        if (isDisposed) return emptyList()
        val baseTables = super.tables()
        return runOnDatabaseIo {
            synchronized(this) {
                val all = LinkedHashSet<String>()
                all.addAll(baseTables)
                all.addAll(knownTableColumns.keys)
                val db = getOrOpenSqliteDatabase()
                if (db != null) {
                    runCatching {
                        db.rawQuery(
                            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                            null
                        ).use { c ->
                            while (c.moveToNext()) {
                                val name = c.getString(0)
                                if (!name.isNullOrEmpty()) {
                                    all.add(name)
                                }
                            }
                        }
                    }
                }
                all.toList()
            }
        }
    }

    override fun dispose() {
        synchronized(this) {
            super.dispose()
            verifiedSchemaTables.clear()
            val helper = openHelper
            openHelper = null
            val db = sqliteDatabase
            sqliteDatabase = null
            if (helper != null) {
                runCatching { helper.close() }
            }
            if (db != null) {
                runCatching { db.close() }
            }
        }
    }

    private fun getOrOpenSqliteDatabase(): SQLiteDatabase? {
        if (sqliteUnavailable) return null
        val existing = sqliteDatabase
        if (existing != null && runCatching { existing.isOpen }.getOrDefault(false)) {
            return existing
        }
        return try {
            val trimmedPath = filePath.trim()
            val ctx = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
            val opened = if (trimmedPath == ":memory:") {
                if (ctx != null) {
                    val helper = AndroidSqliteOpenHelper(ctx, null, version.coerceAtLeast(1))
                    openHelper = helper
                    helper.writableDatabase
                } else {
                    SQLiteDatabase.create(null).also {
                        it.version = version.coerceAtLeast(1)
                    }
                }
            } else {
                val dbFile = resolveAndroidDatabaseFile()
                dbFile.parentFile?.mkdirs()
                if (ctx != null) {
                    val helper = AndroidSqliteOpenHelper(ctx, dbFile.absolutePath, version.coerceAtLeast(1))
                    openHelper = helper
                    helper.writableDatabase
                } else {
                    SQLiteDatabase.openOrCreateDatabase(dbFile, null).also {
                        it.version = version.coerceAtLeast(1)
                    }
                }
            }
            sqliteDatabase = opened
            opened
        } catch (_: Throwable) {
            sqliteUnavailable = true
            null
        }
    }

    private fun resolveAndroidDatabaseFile(): File {
        val ctx = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
        val trimmed = filePath.trim()
        if (!trimmed.contains("://") && File(trimmed).isAbsolute) {
            return File(trimmed)
        }
        val cleanName = databaseName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "GenericUI" }
        val fileName = if (cleanName.endsWith(".db")) cleanName else "$cleanName.db"
        if (ctx != null) {
            val dbPath = runCatching { ctx.getDatabasePath(fileName) }.getOrNull()
            if (dbPath != null) return dbPath
            val filesDir = runCatching { ctx.filesDir }.getOrNull()
            if (filesDir != null) return File(filesDir, fileName)
        }
        return File(resolveDatabaseFilePath(databaseName, filePath, fallbackFolderName = "genericui_android_db"))
    }

    private fun ensureTableAndColumnsForRecord(
        db: SQLiteDatabase,
        normTable: String,
        pkCol: String,
        record: DatabaseRecord
    ) {
        val existingCols = knownTableColumns[normTable]
        if (existingCols == null && !tableExistsInSqlite(db, normTable)) {
            val resolvedSchema = buildOrLookupSchemaForRecord(normTable, pkCol, record)
            for (ddl in DatabaseQuerySqlTranslator.buildCreateTableSql(resolvedSchema)) {
                executePreparedStatement(db, ddl)
            }
        }

        val newCols = collectMissingColumnsForRecord(normTable, record) {
            querySqliteTableColumns(db, normTable)
        }
        if (newCols.isNotEmpty()) {
            ensureRecordColumnsExist(db, normTable, newCols)
        }
    }

    private fun querySqliteTableColumns(
        db: SQLiteDatabase,
        normTable: String
    ): LinkedHashMap<String, DatabaseColumnType> {
        val cols = LinkedHashMap<String, DatabaseColumnType>()
        val tableIdent = DatabaseQuerySqlTranslator.quoteIdentifier(normTable)
        runCatching {
            db.rawQuery("PRAGMA table_info($tableIdent)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                val typeIdx = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    val colName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val typeStr = if (typeIdx >= 0) cursor.getString(typeIdx) else ""
                    if (!colName.isNullOrEmpty()) {
                        cols[colName] = mapSqliteAffinityToColumnType(typeStr)
                    }
                }
            }
        }
        return cols
    }

    private fun ensureRecordColumnsExist(
        db: SQLiteDatabase,
        normTable: String,
        columnsToAdd: Map<String, DatabaseColumnType>
    ) {
        val tableIdent = DatabaseQuerySqlTranslator.quoteIdentifier(normTable)
        for ((colName, colType) in columnsToAdd) {
            val colIdent = DatabaseQuerySqlTranslator.quoteIdentifier(colName)
            val alterSql = "ALTER TABLE $tableIdent ADD COLUMN $colIdent ${colType.sqlAffinity}"
            runCatching { db.execSQL(alterSql) }
        }
    }

    private fun tableExistsInSqlite(db: SQLiteDatabase, normTable: String): Boolean {
        val sanitized = DatabaseQuerySqlTranslator.sanitizeIdentifier(normTable)
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(sanitized)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun executePreparedStatement(db: SQLiteDatabase, prepared: DatabasePreparedSql) {
        db.compileStatement(prepared.sql).use { stmt ->
            bindAll(stmt, prepared.bindValues)
            stmt.execute()
        }
    }

    private fun executePreparedInsert(db: SQLiteDatabase, prepared: DatabasePreparedSql): Long {
        db.compileStatement(prepared.sql).use { stmt ->
            bindAll(stmt, prepared.bindValues)
            return stmt.executeInsert()
        }
    }

    private fun executePreparedUpdateDelete(db: SQLiteDatabase, prepared: DatabasePreparedSql): Int {
        db.compileStatement(prepared.sql).use { stmt ->
            bindAll(stmt, prepared.bindValues)
            return stmt.executeUpdateDelete()
        }
    }

    private fun bindAll(stmt: SQLiteStatement, bindValues: List<DatabaseValue>) {
        stmt.clearBindings()
        for (i in bindValues.indices) {
            val idx = i + 1
            val v = bindValues[i]
            if (v.isNull) {
                stmt.bindNull(idx)
                continue
            }
            when (v.type) {
                DatabaseColumnType.STRING -> stmt.bindString(idx, v.stringValue ?: "")
                DatabaseColumnType.INT64 -> stmt.bindLong(idx, v.longValue ?: 0L)
                DatabaseColumnType.FLOAT64 -> stmt.bindDouble(idx, v.doubleValue ?: 0.0)
                DatabaseColumnType.BOOLEAN -> stmt.bindLong(idx, if (v.booleanValue == true) 1L else 0L)
                DatabaseColumnType.BLOB -> stmt.bindBlob(idx, v.blobValue?.toByteArray() ?: ByteArray(0))
                null -> stmt.bindNull(idx)
            }
        }
    }

    private fun executeSelectCursor(
        db: SQLiteDatabase,
        prepared: DatabasePreparedSql,
        normTable: String,
        selectedColumns: List<String>
    ): List<DatabaseRecord> {
        val factory = SQLiteDatabase.CursorFactory { _, masterQuery, editTable, queryObj ->
            for (i in prepared.bindValues.indices) {
                val idx = i + 1
                val v = prepared.bindValues[i]
                if (v.isNull) {
                    queryObj.bindNull(idx)
                } else {
                    when (v.type) {
                        DatabaseColumnType.STRING -> queryObj.bindString(idx, v.stringValue ?: "")
                        DatabaseColumnType.INT64 -> queryObj.bindLong(idx, v.longValue ?: 0L)
                        DatabaseColumnType.FLOAT64 -> queryObj.bindDouble(idx, v.doubleValue ?: 0.0)
                        DatabaseColumnType.BOOLEAN -> queryObj.bindLong(idx, if (v.booleanValue == true) 1L else 0L)
                        DatabaseColumnType.BLOB -> queryObj.bindBlob(idx, v.blobValue?.toByteArray() ?: ByteArray(0))
                        null -> queryObj.bindNull(idx)
                    }
                }
            }
            SQLiteCursor(masterQuery, editTable, queryObj)
        }
        db.rawQueryWithFactory(factory, prepared.sql, null, normTable).use { cursor ->
            return readCursorRecords(cursor, normTable, selectedColumns)
        }
    }

    private fun readCursorRecords(
        cursor: Cursor,
        normTable: String,
        selectedColumns: List<String>
    ): List<DatabaseRecord> {
        val out = ArrayList<DatabaseRecord>()
        val colCount = cursor.columnCount
        val pkCol = schemas[normTable]?.primaryKeyColumn ?: "key"
        val pkSanitized = DatabaseQuerySqlTranslator.sanitizeIdentifier(pkCol)
        val schemaColBySanitized = buildSchemaColumnLookup(normTable)

        while (cursor.moveToNext()) {
            val values = LinkedHashMap<String, DatabaseValue>()
            var resolvedKey = ""
            for (i in 0 until colCount) {
                val rawName = cursor.getColumnName(i) ?: continue
                val sanitized = DatabaseQuerySqlTranslator.sanitizeIdentifier(rawName)
                val mapped = schemaColBySanitized[sanitized]
                val logicalColName = mapped?.first ?: rawName
                val expectedType = mapped?.second

                val fieldType = cursor.getType(i)
                val dbValue = if (fieldType == Cursor.FIELD_TYPE_NULL || cursor.isNull(i)) {
                    DatabaseValue.Null
                } else {
                    when (expectedType) {
                        DatabaseColumnType.BOOLEAN -> DatabaseValue.of(cursor.getLong(i) != 0L)
                        DatabaseColumnType.INT64 -> DatabaseValue.of(cursor.getLong(i))
                        DatabaseColumnType.FLOAT64 -> DatabaseValue.of(cursor.getDouble(i))
                        DatabaseColumnType.BLOB -> DatabaseValue.of(cursor.getBlob(i) ?: ByteArray(0))
                        DatabaseColumnType.STRING -> DatabaseValue.of(cursor.getString(i))
                        null -> when (fieldType) {
                            Cursor.FIELD_TYPE_INTEGER -> DatabaseValue.of(cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> DatabaseValue.of(cursor.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> DatabaseValue.of(cursor.getBlob(i) ?: ByteArray(0))
                            else -> DatabaseValue.of(cursor.getString(i))
                        }
                    }
                }
                values[logicalColName] = dbValue
                if (sanitized == pkSanitized || logicalColName == pkCol) {
                    resolvedKey = dbValue.asString() ?: ""
                }
            }
            val record = DatabaseRecord(key = resolvedKey, values = values)
            out.add(if (selectedColumns.isEmpty()) record else record.project(selectedColumns))
        }
        return out
    }

    private class AndroidSqliteOpenHelper(
        context: Context,
        dbPath: String?,
        dbVersion: Int
    ) : SQLiteOpenHelper(context, dbPath, null, dbVersion) {
        override fun onCreate(db: SQLiteDatabase) {
            // Tables are created lazily per GenericUI DatabaseTableSchema DDL.
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Schema additive column migrations happen dynamically per upsert.
        }
    }
}
