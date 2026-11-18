package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM tasks WHERE completada = 1")
    suspend fun deleteCompletedTasks()

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(tasks: List<TaskEntity>) {
        clearAll()
        if (tasks.isNotEmpty()) {
            upsertTasks(tasks)
        }
    }
}

@Dao
interface DailyNoteDao {
    @Query("SELECT * FROM daily_notes")
    fun observeAllNotes(): Flow<List<DailyNoteEntity>>

    @Query("SELECT * FROM daily_notes")
    suspend fun getAllNotesOnce(): List<DailyNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: DailyNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<DailyNoteEntity>)

    @Query("DELETE FROM daily_notes WHERE fecha = :fecha")
    suspend fun deleteNoteByDate(fecha: String)

    @Query("DELETE FROM daily_notes")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(notes: List<DailyNoteEntity>) {
        clearAll()
        if (notes.isNotEmpty()) {
            upsertNotes(notes)
        }
    }
}

@Dao
interface DiaryEntryDao {
    @Query("SELECT * FROM diary_entries")
    fun observeAllEntries(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entries")
    suspend fun getAllEntriesOnce(): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entries WHERE fecha = :fecha AND tipo = 'manual' LIMIT 1")
    suspend fun getManualEntryForDate(fecha: String): DiaryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DiaryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<DiaryEntryEntity>)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM diary_entries")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(entries: List<DiaryEntryEntity>) {
        clearAll()
        if (entries.isNotEmpty()) {
            upsertEntries(entries)
        }
    }
}
