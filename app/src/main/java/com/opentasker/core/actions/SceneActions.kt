package com.opentasker.core.actions

import com.opentasker.core.storage.toDomain
import android.provider.Settings
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult
import com.opentasker.core.scenes.SceneOverlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Displays a saved Scene as the same floating overlay used by the Scene library. */
class ShowSceneAction : DeclaredAction(ActionCatalog.require("scene.show")) {

    override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
        val sceneId = args["scene_id"]?.trim()?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: return ActionResult.Failure("Choose a saved scene to show")

        val canDrawOverlays = try {
            Settings.canDrawOverlays(ctx.app)
        } catch (error: Exception) {
            return ActionResult.Failure("Could not check Display over other apps access: ${error.message}", error)
        }
        if (!canDrawOverlays) {
            return ActionResult.Failure(
                "Display over other apps access is required to show scenes. Enable it in Setup.",
            )
        }

        return try {
            val sceneEntity = withContext(Dispatchers.IO) {
                OpenTaskerApp_NoHilt.awaitDb().sceneDao().getById(sceneId)
            } ?: return ActionResult.Failure("The selected scene no longer exists. Choose another scene.")

            val scene = sceneEntity.toDomain()
            SceneOverlayService.show(ctx.app, scene)
            ctx.logger("Show scene: ${scene.name} (id=${scene.id})")
            ActionResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ActionResult.Failure("Could not show scene: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }
}
