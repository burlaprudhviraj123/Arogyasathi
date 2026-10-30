package org.arogyasathi.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for frontline ASHA worker profiles (`ashas` table).
 */
@Dao
interface AshaDao {
    @Query("SELECT * FROM ashas ORDER BY asha_id ASC")
    fun observeAllAshas(): Flow<List<AshaEntity>>

    @Query("SELECT * FROM ashas ORDER BY asha_id ASC")
    fun getAllAshas(): List<AshaEntity>

    @Query("SELECT * FROM ashas WHERE asha_id = :ashaId LIMIT 1")
    fun getAshaById(ashaId: String): AshaEntity?

    @Upsert
    suspend fun upsertAll(ashas: List<AshaEntity>)
}

/**
 * Room DAO for rural households (`households` table).
 */
@Dao
interface HouseholdDao {
    @Query("SELECT * FROM households ORDER BY household_id ASC")
    fun observeAllHouseholds(): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM households WHERE village = :village ORDER BY household_id ASC")
    fun observeHouseholdsByVillage(village: String): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM households ORDER BY household_id ASC")
    fun getAllHouseholds(): List<HouseholdEntity>

    @Query("SELECT * FROM households WHERE village = :village ORDER BY household_id ASC")
    fun getHouseholdsByVillage(village: String): List<HouseholdEntity>

    @Query("SELECT * FROM households WHERE household_id = :householdId LIMIT 1")
    fun getHouseholdById(householdId: String): HouseholdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(households: List<HouseholdEntity>)
}

/**
 * Room DAO for individual household register members (`members` table — 2,234 beneficiaries).
 */
@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY member_id ASC")
    fun observeAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE village = :village ORDER BY member_id ASC")
    fun observeMembersByVillage(village: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY member_id ASC")
    fun getAllMembers(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE village = :village ORDER BY member_id ASC")
    fun getMembersByVillage(village: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE household_id = :householdId ORDER BY member_id ASC")
    fun getMembersByHousehold(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE member_id = :memberId LIMIT 1")
    fun getMemberById(memberId: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberEntity)
}

/**
 * Room DAO for official clinical protocols (`protocols` table — 107 leaf sections).
 */
@Dao
interface ProtocolDao {
    @Query("SELECT * FROM protocols ORDER BY section_id ASC")
    fun observeAllProtocols(): Flow<List<ProtocolSectionEntity>>

    @Query("SELECT * FROM protocols ORDER BY section_id ASC")
    fun getAllProtocols(): List<ProtocolSectionEntity>

    @Query("SELECT * FROM protocols WHERE category = :category ORDER BY section_id ASC")
    fun getProtocolsByCategory(category: String): List<ProtocolSectionEntity>

    @Query("SELECT * FROM protocols WHERE section_id = :sectionId LIMIT 1")
    fun getProtocolById(sectionId: String): ProtocolSectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(protocols: List<ProtocolSectionEntity>)
}

/**
 * Room DAO for completed/rescheduled/referred visit outcomes (`visit_outcomes` table).
 */
@Dao
interface VisitOutcomeDao {
    @Query("SELECT * FROM visit_outcomes ORDER BY timestamp DESC")
    fun observeAllOutcomes(): Flow<List<VisitOutcomeEntity>>

    @Query("SELECT * FROM visit_outcomes ORDER BY timestamp DESC")
    fun getAllOutcomes(): List<VisitOutcomeEntity>

    @Query("SELECT * FROM visit_outcomes WHERE member_id = :memberId ORDER BY timestamp DESC")
    fun getOutcomesForMember(memberId: String): List<VisitOutcomeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: VisitOutcomeEntity)
}

/**
 * Room DAO for emergency danger-sign escalations & referral slips (`referral_slips` / `danger_escalations`).
 */
@Dao
interface DangerEscalationDao {
    @Query("SELECT * FROM referral_slips ORDER BY timestamp DESC")
    fun observeAllEscalations(): Flow<List<DangerEscalationEntity>>

    @Query("SELECT * FROM referral_slips ORDER BY timestamp DESC")
    fun getAllEscalations(): List<DangerEscalationEntity>

    @Query("SELECT * FROM referral_slips WHERE patient_id = :patientId ORDER BY timestamp DESC")
    fun getEscalationsForMember(patientId: String): List<DangerEscalationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEscalation(escalation: DangerEscalationEntity)
}

/**
 * Room DAO for the transactional offline sync outbox queue (`sync_queue` table).
 */
@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY created_at DESC")
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY created_at DESC")
    fun getAllSyncItems(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    fun getPendingSyncItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Transaction
    @Query("UPDATE sync_queue SET status = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSyncedByIds(ids: List<Long>)

    @Query("UPDATE sync_queue SET status = 'SYNCED' WHERE status = 'PENDING'")
    suspend fun markAllPendingSynced()
}

/**
 * Room DAO for longitudinal per-patient consultation history (`patient_chat_history` table).
 */
@Dao
interface PatientChatDao {
    @Query("SELECT * FROM patient_chat_history WHERE patient_id = :patientId ORDER BY timestamp ASC")
    fun observeChatForMember(patientId: String): Flow<List<PatientChatEntity>>

    @Query("SELECT * FROM patient_chat_history WHERE patient_id = :patientId ORDER BY timestamp ASC")
    fun getChatForMember(patientId: String?): List<PatientChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: PatientChatEntity)
}
