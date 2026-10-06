package com.intelliverse.localai

import ai.localstudio.app.llama.Admission
import ai.localstudio.sdk.CheckResult
import ai.localstudio.sdk.LocalCapability
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The router on #169's figures: Gemma 4 E4B chosen, 6401 MB needed, 5861 MB available. */
class ModelRouterTest {
    private val both = setOf(ModelPurpose.CHAT, ModelPurpose.TRANSLATION)
    private val e4b = LocalModelSeed("gemma-4-e4b-it-q4", "Gemma 4 E4B", emptyList(), "E4B", approxSizeBytes = 4_980_000_000, purposes = both)
    private val e2b = LocalModelSeed("gemma-4-e2b-it-q4", "Gemma 4 E2B", emptyList(), "E2B", approxSizeBytes = 3_350_000_000, purposes = both)
    private val tiny = LocalModelSeed("qwen3.5-0.8b-q4", "Qwen3.5 0.8B", emptyList(), "0.8B", approxSizeBytes = 550_000_000, purposes = both)
    private val translator = LocalModelSeed("translategemma-4b", "TranslateGemma 4B", emptyList(), "4B", approxSizeBytes = 2_500_000_000)

    private val required = mapOf(e4b.id to 6_401_000_000L, e2b.id to 5_592_000_000L, tiny.id to 800_000_000L, translator.id to 3_250_000_000L)
    private var available = 5_861_000_000L
    private val checks = mutableMapOf<Pair<String, LocalCapability>, CheckResult>()
    private var chosen: String? = e4b.id

    private fun router(models: List<LocalModelSeed> = listOf(tiny, e2b, e4b, translator)) = ModelRouter(
        installed = { models },
        capabilitiesOf = { if (ModelPurpose.CHAT in it.purposes) setOf(LocalCapability.TEXT, LocalCapability.TRANSLATION) else setOf(LocalCapability.TRANSLATION) },
        checkOf = { seed, cap -> checks[seed.id to cap] },
        chosenFor = { chosen },
        admission = { seed, _ ->
            val need = required.getValue(seed.id)
            if (need <= available) Admission.Admitted(need, available, resident = false)
            else Admission.NotAdmitted(need, available, Admission.Reason.INSUFFICIENT_MEMORY)
        },
    )

    @Test
    fun `the chosen model that does not fit is skipped for the next one, with the figures`() {
        val route = router().route(AiRequest(LocalCapability.TEXT))
        assertTrue(route is RouteResult.Local)
        assertEquals(e2b.id, (route as RouteResult.Local).seed.id)
        assertEquals(listOf(e4b.id), route.skipped.map { it.modelId })
        assertEquals(6_401_000_000L, route.skipped.single().requiredBytes)
    }

    @Test
    fun `the chosen model that fits answers`() {
        available = 8_000_000_000
        assertEquals(e4b.id, (router().route(AiRequest(LocalCapability.TEXT)) as RouteResult.Local).seed.id)
    }

    @Test
    fun `a model that failed this capability's check here is passed over`() {
        available = 8_000_000_000
        checks[e4b.id to LocalCapability.TEXT] = CheckResult.FAIL
        assertEquals(e2b.id, (router().route(AiRequest(LocalCapability.TEXT)) as RouteResult.Local).seed.id)
    }

    @Test
    fun `translation goes to a dedicated translator before chat models`() {
        chosen = null
        assertEquals(translator.id, (router().route(AiRequest(LocalCapability.TRANSLATION)) as RouteResult.Local).seed.id)
    }

    @Test
    fun `nothing fits -- the cloud when allowed, else a reason with every model's figures`() {
        available = 500_000_000
        assertTrue(router().route(AiRequest(LocalCapability.TEXT, allowCloud = true)) is RouteResult.Cloud)
        val none = router().route(AiRequest(LocalCapability.TEXT))
        assertTrue(none is RouteResult.NoModel)
        assertEquals(3, none.skipped.size)
        assertTrue((none as RouteResult.NoModel).reason, "no on-device model fits now" in none.reason)
    }

    @Test
    fun `nothing fits -- the reason names a catalog model that would`() {
        // #172: E2B and E4B could see, 4.7 GB was free; Qwen3.5 0.8B's vision part was not downloaded.
        available = 4_671_000_000
        val router = ModelRouter(
            installed = { listOf(e2b, e4b) },
            capabilitiesOf = { setOf(LocalCapability.TEXT, LocalCapability.VISION) },
            checkOf = { _, _ -> null },
            chosenFor = { null },
            admission = { seed, _ -> Admission.NotAdmitted(required.getValue(seed.id), available, Admission.Reason.INSUFFICIENT_MEMORY) },
            obtainable = { listOf(tiny) },
        )
        val none = router.route(AiRequest(LocalCapability.VISION)) as RouteResult.NoModel
        assertTrue(none.reason, "Qwen3.5 0.8B would fit" in none.reason)
    }

    @Test
    fun `a model that fits but whose vision projector would not is skipped for vision`() {
        // #176: Gemma 4 E2B's weights fit (resident), but its projector needs more than is really left.
        available = 1_576_000_000
        val router = ModelRouter(
            installed = { listOf(e2b) },
            capabilitiesOf = { setOf(LocalCapability.TEXT, LocalCapability.VISION) },
            checkOf = { _, _ -> null },
            chosenFor = { null },
            admission = { _, _ -> Admission.Admitted(0L, available, resident = true) },
            visionNeedBytes = { 2_381_000_000L },
        )
        val none = router.route(AiRequest(LocalCapability.VISION)) as RouteResult.NoModel
        assertTrue(none.reason, "vision part needs" in none.reason)
        assertEquals(1, none.skipped.size)
    }

    @Test
    fun `a model refused at the load is not offered again`() {
        available = 8_000_000_000
        assertEquals(e2b.id, (router().route(AiRequest(LocalCapability.TEXT, exclude = setOf(e4b.id))) as RouteResult.Local).seed.id)
    }
}
