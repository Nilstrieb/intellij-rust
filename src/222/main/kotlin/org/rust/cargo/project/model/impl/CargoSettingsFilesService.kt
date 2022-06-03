/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.RsFileType

@Service
class CargoSettingsFilesService(private val project: Project) {

    @Volatile
    private var settingsFilesCache: Map<String, SettingFileType>? = null

    fun collectSettingsFiles(useCache: Boolean): Map<String, SettingFileType> {
        return if (useCache) {
            settingsFilesCache ?: collectSettingsFiles()
        } else {
            collectSettingsFiles()
        }
    }

    private fun collectSettingsFiles(): Map<String, SettingFileType> {
        val result = mutableMapOf<String, SettingFileType>()
        for (cargoProject in project.cargoProjects.allProjects) {
            result += cargoProject.collectSettingsFiles()
        }

        settingsFilesCache = result

        return result
    }

    private fun CargoProject.collectSettingsFiles(): Map<String, SettingFileType> {
        val files = mutableMapOf(
            manifest.toString() to SettingFileType.CONFIG,
            manifest.parent.resolve(CargoConstants.LOCK_FILE).toString() to SettingFileType.CONFIG
        )

        files += IMPLICIT_TARGET_FILES.mapNotNull {
            val path = rootDir?.findFileByRelativePath(it)?.path ?: return@mapNotNull null
            path to SettingFileType.IMPLICIT_TARGET
        }

        files += IMPLICIT_TARGET_DIRS
            .mapNotNull { rootDir?.findFileByRelativePath(it) }
            .flatMap { VfsUtil.collectChildrenRecursively(it) }
            .mapNotNull { if (it.fileType == RsFileType) it.path to SettingFileType.IMPLICIT_TARGET else null }

        return files
    }

    companion object {
        fun getInstance(project: Project): CargoSettingsFilesService = project.service()

        private val IMPLICIT_TARGET_FILES = listOf(
            "build.rs", "src/main.rs", "src/lib.rs"
        )

        private val IMPLICIT_TARGET_DIRS = listOf(
            "src/bin", "examples", "tests", "benches"
        )
    }

    enum class SettingFileType {
        CONFIG,
        IMPLICIT_TARGET
    }
}
