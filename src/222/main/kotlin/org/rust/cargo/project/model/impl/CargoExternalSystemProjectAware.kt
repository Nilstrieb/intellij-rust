/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.DisposableWrapperList
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoSettingsFilesService.SettingFileType

@Suppress("UnstableApiUsage")
class CargoExternalSystemProjectAware(
    private val project: Project
) : ExternalSystemProjectAware {

    private val listeners = DisposableWrapperList<ExternalSystemProjectListener>()

    override val projectId: ExternalSystemProjectId = ExternalSystemProjectId(CARGO_SYSTEM_ID, project.name)
    override val settingsFiles: Set<String>
        get() {
            val settingsFilesService = CargoSettingsFilesService.getInstance(project)
            // Always collect fresh settings files
            return settingsFilesService.collectSettingsFiles(useCache = false).keys
        }

    override fun isIgnoredSettingsFileEvent(path: String, context: ExternalSystemSettingsFilesModificationContext): Boolean {
        if (super.isIgnoredSettingsFileEvent(path, context)) return true
//        if (path.endsWith("/Cargo.lock") && context.modificationType == ExternalSystemModificationType.EXTERNAL && (context.reloadStatus == IN_PROGRESS || context.reloadStatus == JUST_FINISHED)) return true

        if (context.event != ExternalSystemSettingsFilesModificationContext.Event.UPDATE) return false

        // `isIgnoredSettingsFileEvent` is called just to filter settings files already detected by `settingsFiles` call,
        // so we don't need to collect fresh settings file list, and we can use cached value.
        // Also, `isIgnoredSettingsFileEvent` is called from EDT so using cache should make it much faster
        val settingsFiles = CargoSettingsFilesService.getInstance(project).collectSettingsFiles(useCache = true)
        return settingsFiles[path] == SettingFileType.IMPLICIT_TARGET
    }

    override fun reloadProject(context: ExternalSystemProjectReloadContext) {
        FileDocumentManager.getInstance().saveAllDocuments()
        project.cargoProjects.refreshAllProjects()
    }

    override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
        project.messageBus.connect(parentDisposable).subscribe(CargoSyncTask.CARGO_SYNC_TASK_TOPIC, object : CargoSyncTask.CargoSyncTaskListener {
            override fun onUpdateStarted() {
                listener.onProjectReloadStart()
            }

            override fun onUpdateFinished(result: CargoSyncTask.SyncTaskResult) {
                val status = when (result) {
                    CargoSyncTask.SyncTaskResult.SUCCESS -> ExternalSystemRefreshStatus.SUCCESS
                    CargoSyncTask.SyncTaskResult.FAILURE -> ExternalSystemRefreshStatus.FAILURE
                    CargoSyncTask.SyncTaskResult.CANCEL -> ExternalSystemRefreshStatus.CANCEL
                }
                listener.onProjectReloadFinish(status)
            }
        })
        listeners.add(listener, parentDisposable)
    }

    companion object {
        private val CARGO_SYSTEM_ID: ProjectSystemId = ProjectSystemId("Cargo")
    }
}
