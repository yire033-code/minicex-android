package com.example.minicex.ui.utils

import android.content.Context
import android.view.View
import com.google.android.material.snackbar.Snackbar
import android.graphics.Color
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TutorialManager — State machine for the 23-step interactive tutorial.
 *
 * PHASES:
 *  0  = Not started (show Welcome dialog)
 *  1  = Home (4 steps: 1-4)
 *  2  = Settings (2 steps: 5-6)
 *  3  = Evaluation Step1 (6 steps: 7-12)
 *  4  = Evaluation Step2 (4 steps: 13-16)
 *  5  = Evaluation Step3 (2 steps: 17-18)
 *  6  = Evaluation Step4 (1 step: 19)
 *  7  = Evaluation detail (4 steps: 20-23)
 * 99  = Done / skipped
 */
object TutorialManager {

    private const val PREFS_NAME = "minicex_tutorial_prefs"
    private const val KEY_PHASE = "tutorial_phase"
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    const val PHASE_NOT_STARTED = 0
    const val PHASE_HOME        = 1
    const val PHASE_SETTINGS    = 2
    const val PHASE_STEP1       = 3
    const val PHASE_STEP2       = 4
    const val PHASE_STEP3       = 5
    const val PHASE_STEP4       = 6
    const val PHASE_DETAIL      = 7
    const val PHASE_DONE        = 99

    // First step number of each phase (for display "Paso X de 23")
    const val START_STEP_HOME     = 1
    const val START_STEP_SETTINGS = 5
    const val START_STEP_STEP1    = 7
    const val START_STEP_STEP2    = 13
    const val START_STEP_STEP3    = 17
    const val START_STEP_STEP4    = 19
    const val START_STEP_DETAIL   = 20

    fun getCurrentPhase(context: Context): Int {
        return prefs(context).getInt(KEY_PHASE, PHASE_NOT_STARTED)
    }

    fun setPhase(context: Context, phase: Int) {
        prefs(context).edit().putInt(KEY_PHASE, phase).apply()
    }

    fun startTutorial(context: Context) = setPhase(context, PHASE_HOME)

    fun advancePhase(context: Context) {
        val next = when (getCurrentPhase(context)) {
            PHASE_HOME      -> PHASE_SETTINGS
            PHASE_SETTINGS  -> PHASE_STEP1
            PHASE_STEP1     -> PHASE_STEP2
            PHASE_STEP2     -> PHASE_STEP3
            PHASE_STEP3     -> PHASE_STEP4
            PHASE_STEP4     -> PHASE_DETAIL
            PHASE_DETAIL    -> PHASE_DONE
            else            -> PHASE_DONE
        }
        setPhase(context, next)
    }

    fun skipAll(context: Context) {
        setPhase(context, PHASE_DONE)
        cleanupScope.launch { cleanupDemoData(context) }
    }

    suspend fun cleanupDemoData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val db = com.example.minicex.data.local.AppDatabase.getDatabase(appContext)
            db.withTransaction {
                val demoStudent = db.studentDao().getStudentByMatricula("DEMO001")
                if (demoStudent != null) {
                    db.evaluationDao().deleteRubricDetailsForStudent(demoStudent.idAlumno)
                    db.evaluationDao().deleteEvaluationsForStudent(demoStudent.idAlumno)
                }
                db.syncQueueDao().deleteDemoQueueActions()
                db.studentDao().deleteStudentByMatricula("DEMO001")
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("TutorialManager", "Error deleting tutorial data: ${e.message}", e)
            false
        }
    }

    fun isDone(context: Context) = getCurrentPhase(context) == PHASE_DONE
    fun isNotStarted(context: Context) = getCurrentPhase(context) == PHASE_NOT_STARTED

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Blue Snackbar that tells the user where to navigate next. */
    fun showNavigationHint(view: View, message: String) {
        Snackbar.make(view, message, 5000).apply {
            setBackgroundTint(Color.parseColor("#1E3A8A"))
            setTextColor(Color.WHITE)
            setActionTextColor(Color.parseColor("#FCD34D"))
            setAction("OK") { dismiss() }
        }.show()
    }
}
