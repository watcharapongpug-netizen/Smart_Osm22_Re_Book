package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(household: Household): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(households: List<Household>): List<Long>

    @Update
    suspend fun update(household: Household)

    @Delete
    suspend fun delete(household: Household)

    @Query("DELETE FROM households WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM households WHERE householdUuid = :uuid")
    suspend fun deleteByUuid(uuid: String): Int

    @Query("SELECT * FROM households WHERE id = :id LIMIT 1")
    suspend fun getHouseholdById(id: Long): Household?

    @Query("SELECT * FROM households WHERE houseNo = :houseNo LIMIT 1")
    suspend fun getHouseholdByNo(houseNo: String): Household?

    @Query("SELECT * FROM households WHERE householdUuid = :uuid LIMIT 1")
    suspend fun getHouseholdByUuid(uuid: String): Household?

    @Query("SELECT EXISTS(SELECT 1 FROM households WHERE householdUuid = :uuid LIMIT 1)")
    suspend fun existsByHouseholdUuid(uuid: String): Boolean

    @Query("SELECT * FROM households WHERE villageNo = :villageNo ORDER BY houseNo ASC")
    fun getHouseholdsByVillageNo(villageNo: String): Flow<List<Household>>

    @Query("SELECT * FROM households WHERE villageNo = :villageNo ORDER BY houseNo ASC")
    suspend fun getHouseholdsByVillageNoList(villageNo: String): List<Household>

    @Query("SELECT * FROM households WHERE houseNo LIKE '%' || :query || '%' OR villageNo LIKE '%' || :query || '%' ORDER BY houseNo ASC")
    fun searchHouseholds(query: String): Flow<List<Household>>

    @Transaction
    @Query("SELECT * FROM households WHERE villageNo = :villageNo ORDER BY houseNo ASC")
    fun getHouseholdsWithPersonsByVillage(villageNo: String): Flow<List<HouseholdWithPersons>>

    @Query("SELECT * FROM households ORDER BY houseNo ASC")
    suspend fun getAllHouseholds(): List<Household>

    @Query("SELECT COUNT(*) FROM households WHERE villageNo = :villageNo")
    fun getTotalHouseholdsCountByVillage(villageNo: String): Flow<Int>

    @Transaction
    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    fun getHouseholdWithPersonsById(householdId: Long): Flow<HouseholdWithPersons?>

    @Query("""
        SELECT h.id as householdId, h.houseNo, h.villageNo,
        COUNT(p.id) as totalMembers,
        SUM(CASE WHEN p.gender = 'MALE' THEN 1 ELSE 0 END) as males,
        SUM(CASE WHEN p.gender = 'FEMALE' THEN 1 ELSE 0 END) as females,
        SUM(CASE WHEN p.houseStatus = 'HEAD' THEN 1 ELSE 0 END) as owners,
        SUM(CASE WHEN p.houseStatus = 'RESIDENT' THEN 1 ELSE 0 END) as residents,
        SUM(CASE WHEN p.personStatus = 'DEAD' THEN 1 ELSE 0 END) as deceased,
        SUM(CASE WHEN p.personStatus = 'ALIVE' AND (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(p.birthDate, 1, 4) AS INTEGER)) >= 60 THEN 1 ELSE 0 END) as elderly,
        SUM(CASE WHEN p.personStatus = 'ALIVE' AND (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(p.birthDate, 1, 4) AS INTEGER)) BETWEEN 0 AND 5 THEN 1 ELSE 0 END) as earlyChild,
        SUM(CASE WHEN p.personStatus = 'ALIVE' AND (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(p.birthDate, 1, 4) AS INTEGER)) BETWEEN 6 AND 12 THEN 1 ELSE 0 END) as schoolAge,
        SUM(CASE WHEN p.personStatus = 'ALIVE' AND (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(p.birthDate, 1, 4) AS INTEGER)) BETWEEN 13 AND 20 THEN 1 ELSE 0 END) as teenager,
        SUM(CASE WHEN p.personStatus = 'ALIVE' AND (CAST(strftime('%Y', 'now') AS INTEGER) - CAST(substr(p.birthDate, 1, 4) AS INTEGER)) BETWEEN 21 AND 59 THEN 1 ELSE 0 END) as workingAge,
        h.latitude, h.longitude, h.dataStatus as dataStatus,
        (SELECT fullName FROM persons WHERE householdId = h.id AND houseStatus = 'HEAD' LIMIT 1) as headName
        FROM households h
        LEFT JOIN persons p ON h.id = p.householdId
        WHERE h.villageNo = :villageNo
        GROUP BY h.id, h.houseNo, h.villageNo, h.latitude, h.longitude, h.dataStatus
        ORDER BY h.houseNo ASC
    """)
    fun getHouseSummaryByVillage(villageNo: String): Flow<List<HouseSummary>>
}
