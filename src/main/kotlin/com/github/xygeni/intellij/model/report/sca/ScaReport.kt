package com.github.xygeni.intellij.model.report.sca

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable

/**
 * ScaReport
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/

@Serializable
data class ScaReport(
    val metadata: RawReportMetadata,
    val dependencies: List<ScaRaw>
)

@Serializable
data class RemediableRaw(
    val remediableLevel: String? = null,
)

@Serializable
data class SourceRaw(
    val name: String? = null,
    val url: String? = null,
)

@Serializable
data class VersionRaw(
    val versionStartExcluded: Boolean? = null,
    val versionEndExcluded: Boolean? = null,
    val startVersion: String? = null,
    val endVersion: String? = null,
)

@Serializable
data class RatingRaw(
    val method: String? = null,
    val severity: String? = null,
    val score: Double? = null,
    val vector: String? = null,
)

@Serializable
data class VulnerabilityRaw (
    val source: SourceRaw? = null,
    val userId: String? = null,
    val publishDate: String? = null,
    val name: String? = null,
    val id: String = "",
    val lastModified: String? = null,
    val severity: String? = null,
    val description: String? = null,
    val versions: List<VersionRaw>? = null,
    val ratings: List<RatingRaw>? = null,
    val cwes: List<String>? = null,
    val references: List<String>? = null,
    val cve: String? = null,
    val issueId: String? = null,
    val overallCvssScore: Double? = null,
)

@Serializable
data class PathRaw(
    val id: Int? = null,
    val dependencyDescription: String? = null,
    val locations: List<RawIssueLocation>? = null,
    val parents: List<Int>? = null,
    val dependencyPaths: List<String>? = null,
    val directDependency: Boolean? = null,
    val currentProject: Boolean? = null,
    val root: Boolean? = null,
)

@Serializable
data class LicenseRaw(
    val name: String? = null,
    val spxId: String? = null,
    val url: String? = null,
    val confidence: Double? = null,
    val licenseKind: String? = null,
)

@Serializable
data class ScaRaw(
    val actualFilePath: String? = null,
    val fileName: String? = null,
    val virtual: Boolean? = null,
    val type: String? = null,
    val language: String? = null,
    val repositoryType: String? = null,
    val group: String? = null,
    val displayFileName: String = "",
    val licenses: List<LicenseRaw>? = null,
    val name: String? = null,
    val version: String? = null,
    val scope: String? = null,
    val ecosystem: String? = null,
    val hierarchyResolved: Boolean? = null,
    val projectReferences: List<String>? = null,
    val vulnerabilities: List<VulnerabilityRaw>? = null,
    val paths: PathRaw? = null,
    val remediable: RemediableRaw? = null,
    val tags: List<String>? = null,
)

/*const issue = new VulnXygeniIssue({
            remediableLevel: dep.remediable?.remediableLevel,
            kind: 'sca_vulnerability',
            baseScore: this.getBaseScore(vuln.ratings),
            versions: this.summarizeVersionRange(vuln.versions),
            publicationDate: publishDate.toLocaleString(), //  format '2021-01-29T21:15:08Z' as '29 ene, 2021, 21: 15'
 */

fun ScaRaw.toIssue(toolName: String?): List<ScaXygeniIssue> {
    val loc = paths?.locations?.get(0) ?: null
    return vulnerabilities
        ?.map { vuln ->
            ScaXygeniIssue(
                id = vuln.id,
                type = vuln.id,
                detector = vuln.source?.name ?: "unknown",
                tool = toolName,
                severity = vuln.severity ?: "unknown",
                confidence = "",
                category = "sca",
                categoryName = "Vulnerability",
                file = displayFileName,
                explanation = vuln.description ?: "Vulnerability " + vuln.cve,
                tags = (tags ?: emptyList()) + listOfNotNull(remediable?.remediableLevel),
                url = vuln.source?.url ?: "",
                beginLine = loc?.beginLine ?: 0,
                endLine = loc?.endLine ?: 0,
                beginColumn = loc?.beginColumn ?: 0,
                endColumn = loc?.endColumn ?: 0,
                code = loc?.code ?: "",

                virtual = virtual,
                repositoryType = repositoryType,
                displayFileName = displayFileName,
                group = group,
                name = name,
                version = version,
                dependencyPaths = paths?.dependencyPaths,
                directDependency = paths?.directDependency,
                baseScore = vuln.overallCvssScore,
                weakness = vuln.cwes,
                references = vuln.references,
                vector = vuln.ratings?.lastOrNull()?.vector ?: "",
                language = language ?: "",


                )
        }.orEmpty()
}