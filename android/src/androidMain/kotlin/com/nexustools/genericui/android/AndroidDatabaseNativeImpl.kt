package com.nexustools.genericui.android

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Looper
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.io.DatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DatabaseWritableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultDatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultDatabaseWritableNativeImpl
import com.nexustools.genericui.nativeimpl.system.DefaultDatabaseNativeImpl
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabasePreparedSql
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseQuerySqlTranslator
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseTableSchema
import com.nexustools.genericui.util.db.DatabaseValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl]
 * backed by `android.database.sqlite.SQLiteDatabase` / `androidx.sqlite` on [Dispatchers.IO].
 *
 * Translates structured [DatabaseTableSchema], [DatabaseRecord], [DatabaseQuery], and [DatabaseFilter]
 * ASTs via [DatabaseQuerySqlTranslator] into parameterized SQLite statements with zero string query parsing,
 * and falls back transparently to [DefaultDatabaseNativeImpl] when running in headless JVM unit tests
 * without an Android device runtime.
 */
class AndroidDatabaseNativeImpl(
    databaseName: String = "GenericUI",
    filePath: String = "GenericUI.db",
    version: Int = 1,
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : DefaultDatabaseNativeImpl(databaseName, filePath, version) {

    private var sqliteDatabase: SQLiteDatabase? = null
    private var sqliteUnavailable: Boolean = false
    private val knownTableColumns = LinkedHashMap<String, LinkedHashMap<String, DatabaseColumnType>>()
    private var tickSequence: Long = 1700000000000L

    override suspend fun defineTable(schema: DatabaseTableSchema): Boolean {
        if (isDisposed) return false
        super.defineTable(schema)
        val norm = normalizeTableName(schema.tableName)
        return runOnAndroidIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnAndroidIo true
                val statements = DatabaseQuerySqlTranslator.buildCreateTableSql(schema)
                for (stmt in statements) {
                    executePreparedStatement(db, stmt)
                }
                val colMap = knownTableColumns.getOrPut(norm) { LinkedHashMap() }
                val effectiveCols = if (schema.columns.isEmpty()) {
                    DatabaseTableSchema.defaultKeyValueColumns(schema.primaryKeyColumn)
                } else {
                    schema.columns
                }
                for (col in effectiveCols) {
                    colMap[col.name] = col.type
                }
                ensureRecordColumnsExist(db, norm, colMap)
                true
            }
        }
    }

    override suspend fun putRecord(table: String, record: DatabaseRecord): Boolean {
        if (isDisposed) return false
        super.putRecord(table, record)
        val norm = normalizeTableName(table)
        return runOnAndroidIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnAndroidIo true
                val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
                val enrichedValues = LinkedHashMap(record.values)
                if (!enrichedValues.containsKey(pkCol)) {
                    enrichedValues[pkCol] = DatabaseValue.of(record.key)
                }
                if (!enrichedValues.containsKey("updatedAt")) {
                    enrichedValues["updatedAt"] = DatabaseValue.of(++tickSequence)
                }
                val enrichedRecord = DatabaseRecord(key = record.key, values = enrichedValues)
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

    override suspend fun getRecord(table: String, key: String): DatabaseRecord? {
        if (isDisposed) return null
        val norm = normalizeTableName(table)
        val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
        val selectByKeyQuery = DatabaseQuery.from(norm)
            .where(DatabaseFilter.eq(pkCol, key))
            .limit(1)
        return query(selectByKeyQuery).firstOrNull()
    }

    override suspend fun query(query: DatabaseQuery): List<DatabaseRecord> {
        if (isDisposed) return emptyList()
        val norm = normalizeTableName(query.table)
        return runOnAndroidIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnAndroidIo superQueryFallback(query)
                if (!tableExistsInSqlite(db, norm)) {
                    return@runOnAndroidIo emptyList()
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
        return runOnAndroidIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnAndroidIo superCountFallback(query)
                if (!tableExistsInSqlite(db, norm)) {
                    return@runOnAndroidIo 0L
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
        return runOnAndroidIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnAndroidIo memDeleted
                if (!tableExistsInSqlite(db, norm)) {
                    return@runOnAndroidIo 0
                }
                val normalizedQuery = query.copy(table = norm)
                val prepared = DatabaseQuerySqlTranslator.buildDeleteSql(normalizedQuery)
                executePreparedUpdateDelete(db, prepared)
            }
        }
    }

    override suspend fun openReadable(table: String, key: String): DatabaseReadableNativeImpl? {
        val buf = read(table, key) ?: return null
        return DefaultDatabaseReadableNativeImpl(
            table = normalizeTableName(table),
            key = key,
            initialBytes = buf.toByteArray()
        )
    }

    override suspend fun openWritable(table: String, key: String, append: Boolean): DatabaseWritableNativeImpl {
        val initial = if (append) read(table, key)?.toByteArray() ?: ByteArray(0) else ByteArray(0)
        return DefaultDatabaseWritableNativeImpl(
            table = normalizeTableName(table),
            key = key,
            appendMode = append,
            initialBytes = initial
        ) { committed ->
            write(table, key, ByteBuffer.wrap(committed), append = false)
        }
    }

    override suspend fun exists(table: String, key: String): Boolean =
        getRecord(table, key) != null

    override suspend fun delete(table: String, key: String): Boolean {
        if (isDisposed) return false
        val norm = normalizeTableName(table)
        val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
        val deleted = deleteWhere(DatabaseQuery.from(norm).where(DatabaseFilter.eq(pkCol, key)))
        return deleted > 0
    }

    override suspend fun keys(table: String, prefix: String): List<String> {
        if (isDisposed) return emptyList()
        val norm = normalizeTableName(table)
        val pkCol = schemas[norm]?.primaryKeyColumn ?: "key"
        val q = if (prefix.isEmpty()) {
            DatabaseQuery.from(norm).select(pkCol)
        } else {
            DatabaseQuery.from(norm).select(pkCol).where(DatabaseFilter.startsWith(pkCol, prefix))
        }
        return query(q).map { it.key }
    }

    override suspend fun clearTable(table: String): Int =
        deleteWhere(DatabaseQuery.from(table))

    override suspend fun dropTable(table: String): Boolean {
        if (isDisposed) return false
        val memDropped = super.dropTable(table)
        val norm = normalizeTableName(table)
        return runOnAndroidIo {
            synchronized(this) {
                val db = getOrOpenSqliteDatabase() ?: return@runOnAndroidIo memDropped
                val existed = tableExistsInSqlite(db, norm)
                val tableIdent = DatabaseQuerySqlTranslator.quoteIdentifier(norm)
                db.execSQL("DROP TABLE IF EXISTS $tableIdent")
                knownTableColumns.remove(norm)
                existed || memDropped
            }
        }
    }

    override suspend fun tables(): List<String> {
        if (isDisposed) return emptyList()
        val baseTables = super.tables()
        return runOnAndroidIo {
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
            knownTableColumns.clear()
            val db = sqliteDatabase
            sqliteDatabase = null
            if (db != null) {
                runCatching { db.close() }
            }
        }
    }

    private fun superQueryFallback(query: DatabaseQuery): List<DatabaseRecord> {
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return emptyList()
        return query.applyToRecords(store.values)
    }

    private fun superCountFallback(query: DatabaseQuery): Long {
        val norm = normalizeTableName(query.table)
        val store = tableRecords[norm] ?: return 0L
        val activeFilter = query.filter ?: return store.size.toLong()
        return store.values.count { activeFilter.matches(it) }.toLong()
    }

    private fun getOrOpenSqliteDatabase(): SQLiteDatabase? {
        if (sqliteUnavailable) return null
        val existing = sqliteDatabase
        if (existing != null && runCatching { existing.isOpen }.getOrDefault(false)) {
            return existing
        }
        return try {
            val dbFile = resolveAndroidDatabaseFile()
            dbFile.parentFile?.mkdirs()
            val opened = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            opened.version = version.coerceAtLeast(1)
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
        return File(File(System.getProperty("java.io.tmpdir", "."), "genericui_android_db"), fileName)
    }

    private fun ensureTableAndColumnsForRecord(
        db: SQLiteDatabase,
        normTable: String,
        pkCol: String,
        record: DatabaseRecord
    ) {
        val existingCols = knownTableColumns[normTable]
        if (existingCols == null && !tableExistsInSqlite(db, normTable)) {
            val inferredSpecs = ArrayList<DatabaseColumnSpec>()
            inferredSpecs.add(
                DatabaseColumnSpec(
                    name = pkCol,
                    type = DatabaseColumnType.STRING,
                    primaryKey = true,
                    nullable = false
                )
            )
            for ((colName, value) in record.values) {
                if (colName == pkCol) continue
                inferredSpecs.add(
                    DatabaseColumnSpec(
                        name = colName,
                        type = value.type ?: DatabaseColumnType.BLOB,
                        primaryKey = false,
                        nullable = true,
                        indexed = colName == "updatedAt"
                    )
                )
            }
            val inferredSchema = schemas[normTable] ?: DatabaseTableSchema(
                tableName = normTable,
                primaryKeyColumn = pkCol,
                columns = inferredSpecs
            )
            schemas.putIfAbsent(normTable, inferredSchema)
            for (ddl in DatabaseQuerySqlTranslator.buildCreateTableSql(inferredSchema)) {
                executePreparedStatement(db, ddl)
            }
            val map = knownTableColumns.getOrPut(normTable) { LinkedHashMap() }
            for (spec in inferredSchema.columns) {
                map[spec.name] = spec.type
            }
        }

        val colMap = knownTableColumns.getOrPut(normTable) { LinkedHashMap() }
        val newCols = LinkedHashMap<String, DatabaseColumnType>()
        for ((colName, value) in record.values) {
            val sanitized = DatabaseQuerySqlTranslator.sanitizeIdentifier(colName)
            val alreadyPresent = colMap.keys.any {
                DatabaseQuerySqlTranslator.sanitizeIdentifier(it).equals(sanitized, ignoreCase = true)
            }
            val valueType = value.type
            val resolvedType = valueType ?: colMap[colName] ?: DatabaseColumnType.BLOB
            if (!alreadyPresent) {
                newCols[colName] = resolvedType
            }
            if (valueType != null) {
                colMap[colName] = valueType
            }
        }
        if (newCols.isNotEmpty()) {
            ensureRecordColumnsExist(db, normTable, newCols)
        }
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
        val schemaColBySanitized = LinkedHashMap<String, Pair<String, DatabaseColumnType>>()
        schemas[normTable]?.columns?.forEach { spec ->
            schemaColBySanitized[DatabaseQuerySqlTranslator.sanitizeIdentifier(spec.name)] = spec.name to spec.type
        }
        knownTableColumns[normTable]?.forEach { (k, t) ->
            val s = DatabaseQuerySqlTranslator.sanitizeIdentifier(k)
            if (!schemaColBySanitized.containsKey(s)) {
                schemaColBySanitized[s] = k to t
            }
        }

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

    private suspend fun <T> runOnAndroidIo(block: () -> T): T {
        val hasInterceptor = coroutineContext[ContinuationInterceptor] != null
        return if (hasInterceptor || isOnAndroidMainThread()) {
            withContext(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }

    private fun isOnAndroidMainThread(): Boolean =
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
}
