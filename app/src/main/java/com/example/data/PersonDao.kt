package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY id ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons ORDER BY id ASC")
    suspend fun getAllPersonsList(): List<Person>

    @Query("SELECT COUNT(*) FROM persons p INNER JOIN households h ON p.householdId = h.id WHERE h.villageNo = :villageNo")
    fun getTotalPersonsCountByVillage(villageNo: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(person: Person): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(persons: List<Person>): List<Long>

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM persons WHERE personUuid = :uuid")
    suspend fun deleteByUuid(uuid: String): Int

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): Person?

    @Query("SELECT * FROM persons WHERE personUuid = :uuid LIMIT 1")
    suspend fun getPersonByUuid(uuid: String): Person?
    
    @Query("SELECT * FROM persons WHERE nationalId = :nationalId LIMIT 1")
    suspend fun getPersonByNationalId(nationalId: String): Person?
    
    @Query("SELECT * FROM persons WHERE householdId = :householdId")
    fun getPersonsByHouseholdId(householdId: Long): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE householdId = :householdId")
    suspend fun getPersonsByHouseholdIdList(householdId: Long): List<Person>

    @Query("SELECT * FROM persons WHERE fullName LIKE '%' || :query || '%' OR nationalId LIKE '%' || :query || '%' ORDER BY fullName ASC")
    fun searchPersons(query: String): Flow<List<Person>>

    @Query("SELECT p.* FROM persons p INNER JOIN households h ON p.householdId = h.id WHERE h.villageNo = :villageNo ORDER BY p.fullName ASC")
    fun getPersonsByVillage(villageNo: String): Flow<List<Person>>

    @Query("SELECT COUNT(*) FROM persons WHERE personStatus = :status")
    fun getPersonsCountByStatus(status: PersonStatus): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM persons WHERE personUuid = :uuid LIMIT 1)")
    suspend fun existsByPersonUuid(uuid: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM persons WHERE nationalId = :nationalId LIMIT 1)")
    suspend fun existsByNationalId(nationalId: String): Boolean
}
