package org.hildan.chrome.devtools.build

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.hildan.chrome.devtools.build.diff.*
import org.hildan.chrome.devtools.build.json.*
import org.hildan.chrome.devtools.build.json.ChromeJsonRepository
import java.time.LocalDate

private val json = Json { prettyPrint = true }

open class PrintProtocolStatsTask : DefaultTask() {

    init {
        group = "protocol"
    }

    @Input
    var milestoneRefs: List<MilestoneRef> = listOf(
        MilestoneRef("b8266f887dbb538727b7842c37e1c0b6b4ba5cd3", LocalDate.parse("2020-01-10")),
        MilestoneRef("8d9fa2dd95b4393a5ea86a7b8af6bfb0ef139461", LocalDate.parse("2020-06-03")),
        MilestoneRef("4ee1d5ca9419f0d5a5451ae7b6a4ae6effb0bd29", LocalDate.parse("2020-10-05")),
        MilestoneRef("a5b6b3ebc9922bf681fbd260cb0f8bdc75fb067b", LocalDate.parse("2021-01-06")),
        MilestoneRef("8a7c1b5478b87e30544c667e3622097b52dae794", LocalDate.parse("2021-02-01")),
        MilestoneRef("78470ced3293aaa738fe7c3a8dc4b9e8c92ce553", LocalDate.parse("2021-03-01")),
        MilestoneRef("0210b99ee22093ceb35928a63b44300919e20786", LocalDate.parse("2021-04-06")),
        MilestoneRef("2dd45d5caf910d1676018c9874b053cf90f8190a", LocalDate.parse("2021-05-03")),
        MilestoneRef("b531de2899fc03ffa00b81d996dea7ec67c9dd56", LocalDate.parse("2021-07-02")),
        MilestoneRef("39a821010c2cbf4c6dbc893cab6cdf2b17554eb9", LocalDate.parse("2021-08-02")),
        MilestoneRef("e4f6e30233fbc7b633521502527b2bc3db20f9c9", LocalDate.parse("2021-09-02")),
        MilestoneRef("d24ecc63481149f68ff645efd92c607106a5d31c", LocalDate.parse("2021-10-01")),
        MilestoneRef("23061aa23598439b2b9e598c5e1a9735c6bf6c11", LocalDate.parse("2021-11-01")),
        MilestoneRef("11ea32aaecf36154e2326b22d4c6067374d8faf4", LocalDate.parse("2021-12-02")),
        MilestoneRef("90efbcc85076c724bb17e30fd0486917d0bf6271", LocalDate.parse("2022-01-04")),
        MilestoneRef("1d22b7b2621ef447d7a144bd8ba8819c9c98e298", LocalDate.parse("2022-02-02")),
        MilestoneRef("a0800ab4c8612dca98c7b123a3ee28bc12c51d03", LocalDate.parse("2022-03-04")),
        MilestoneRef("7c8b6ad7f5c527c6b1792ce86a66022eb1df904d", LocalDate.parse("2022-04-22")),
        MilestoneRef("a6daed6b1afd2e9eb131146ee6bfb9a764457f36", LocalDate.parse("2022-05-02")),
        MilestoneRef("a3a4df3ebfb40260c5811c53cb0f1907ebf7a3bf", LocalDate.parse("2022-06-01")),
        MilestoneRef("1c2b84ae5e00515fe5141d024c6574cd74531edf", LocalDate.parse("2022-07-05")),
        MilestoneRef("750f43443aeb1796dca9f70f6f2b4e42b0a071d1", LocalDate.parse("2022-08-02")),
        MilestoneRef.MASTER_TODAY,
    )

    @TaskAction
    fun printStats() {
        val sortedRefs = milestoneRefs.sortedBy { it.date }
        val diff = sortedRefs.map { it.fetchAndParseDescriptorsRevision() }.cumulatedDiff()

        println("_______________________")
        println("Cumulated diff between ${sortedRefs.first().date} and ${sortedRefs.last().date}:")
        println(json.encodeToString(diff))
    }

    private fun List<DescriptorsRevision>.cumulatedDiff() = windowed(2)
        .map { (oldRev, newRev) ->
            println("Computing diff between ${oldRev.date} and ${newRev.date}...")
            diffDomains(oldRev.allDomains, newRev.allDomains)
        }
        .fold(DomainDiffStats.ZERO, DomainDiffStats::plus)
}

data class MilestoneRef(
    val commitSha: String,
    val date: LocalDate,
) {
    companion object {
        val MASTER_TODAY = MilestoneRef("master", LocalDate.now())
    }
}

private data class DescriptorsRevision(
    val browser: ChromeProtocolDescriptor,
    val javascript: ChromeProtocolDescriptor,
    val date: LocalDate,
) {
    val allDomains = browser.domains + javascript.domains
}

private fun MilestoneRef.fetchAndParseDescriptorsRevision(): DescriptorsRevision {
    val jsonDescriptorUrls = ChromeJsonRepository.descriptorUrls(commitSha)
    return DescriptorsRevision(
        browser = ChromeProtocolDescriptor.fromJson(jsonDescriptorUrls.browserProtocolUrl.readText()),
        javascript = ChromeProtocolDescriptor.fromJson(jsonDescriptorUrls.jsProtocolUrl.readText()),
        date = date,
    )
}
