package com.chaisteps.app.logic

import com.chaisteps.app.data.ChaiType
import com.chaisteps.app.data.CupSize
import com.chaisteps.app.data.Repository
import com.chaisteps.app.data.Route
import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.data.stepDay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun persistsAndReloads() = runTest {
        val dir = tmp.newFolder()
        val repo = Repository(dir)
        repo.updateProfile { it.copy(name = "Waheed", stepGoal = 9_000) }
        repo.addWeight(100, 90.0)
        repo.addWeight(100, 89.5) // same day replaces
        repo.addWeight(101, 89.0)
        repo.addChai(ChaiType.KARAK, 1.5, CupSize.LARGE, 1_000)
        repo.addManualSteps(100, 500)
        repo.applyStepReading(10_000, 0, 100) // baseline
        repo.applyStepReading(10_400, 0, 100)
        repo.flush()

        val reloaded = Repository(dir).data.value
        assertEquals("Waheed", reloaded.profile.name)
        assertEquals(listOf(89.5, 89.0), reloaded.weights.map { it.kg })
        assertEquals(1, reloaded.chai.size)
        assertEquals(CupSize.LARGE, reloaded.chai[0].size)
        assertEquals(900, reloaded.stepDay(100).total)
        assertEquals(10_400L, reloaded.stepCounter.lastValue)
    }

    @Test fun setDayTotalAdjustsManualPart() = runTest {
        val repo = Repository(tmp.newFolder())
        repo.applyStepReading(0, 0, 5)
        repo.applyStepReading(3_000, 0, 5)
        repo.setDayTotal(5, 5_000)
        assertEquals(5_000, repo.data.value.stepDay(5).total)
        repo.setDayTotal(5, 1_000)
        assertEquals(1_000, repo.data.value.stepDay(5).total)
    }

    @Test fun walksRoutesExportImport() = runTest {
        val dir = tmp.newFolder()
        val repo = Repository(dir)
        val route = Route(listOf(listOf(RoutePoint(25.0, 55.0, 0), RoutePoint(25.001, 55.0, 60_000))))
        repo.addWalk(WalkSession("w1", 0, 60_000, 60_000, 111.0), route)
        repo.updateProfile { it.copy(name = "A") }
        repo.flush()
        assertEquals(route, repo.loadRoute("w1"))

        val exported = repo.exportJson(123)
        val other = Repository(tmp.newFolder())
        other.importJson(exported)
        other.flush()
        assertEquals("A", other.data.value.profile.name)
        assertEquals(1, other.data.value.walks.size)
        assertEquals(route, other.loadRoute("w1"))

        repo.deleteWalk("w1")
        repo.flush()
        assertTrue(repo.data.value.walks.isEmpty())
    }

    @Test(expected = Exception::class)
    fun importRejectsGarbage() {
        Repository(tmp.newFolder()).importJson("{\"hello\":1}")
    }

    @Test fun corruptFileFallsBackToBackup() = runTest {
        val dir = tmp.newFolder()
        val repo = Repository(dir)
        repo.updateProfile { it.copy(name = "first") }
        repo.flush()
        repo.updateProfile { it.copy(name = "second") }
        repo.flush() // backup now holds "first"
        File(dir, "chaisteps.json").writeText("{ broken")
        assertEquals("first", Repository(dir).data.value.profile.name)
    }
}
