package cc.ioctl.tmoe.hook.func

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.FrameLayout
import android.widget.Toast
import cc.ioctl.tmoe.R
import cc.ioctl.tmoe.base.annotation.FunctionHookEntry
import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import cc.ioctl.tmoe.hook.core.ProfileActivityRowHook
import cc.ioctl.tmoe.lifecycle.Parasitics
import cc.ioctl.tmoe.ui.LocaleController
import cc.ioctl.tmoe.util.HostInfo
import cc.ioctl.tmoe.util.Log
import cc.ioctl.tmoe.util.Reflex
import de.robv.android.xposed.XposedHelpers

@FunctionHookEntry
object ShowDCInProfile : CommonDynamicHook(), ProfileActivityRowHook.Callback {
    private val rowName = "SHOW_DC_IN_PROFILE"

    override fun initOnce(): Boolean {
        ProfileActivityRowHook.addCallback(this)
        return true
    }

    override fun onBindViewHolder(
        key: String,
        holder: Any,
        adpater: Any,
        profileActivity: Any
    ): Boolean {
        if (rowName != key) return false
        (Reflex.getInstanceObjectOrNull(holder, "itemView") as? FrameLayout)?.let { textCell ->
            Parasitics.injectModuleResources(HostInfo.getApplication().resources)
            val dcId = getDCId(profileActivity).let {
                if (it <= 0) "Unknown" // maybe 0 when TL_photoEmpty
                else "DC$it"
            }
            val title = LocaleController.getString("DataCenter", R.string.DataCenter)
            XposedHelpers.callMethod(textCell, "setTextAndValue", dcId, title, true)
        }
        return true
    }

    override fun getItemViewType(key: String, adapter: Any, profileActivity: Any): Int {
        if (rowName == key) return 2 // VIEW_TYPE_TEXT_DETAIL
        return -1
    }

    override fun onItemClicked(key: String, adapter: Any, profileActivity: Any): Boolean {
        if (rowName != key) return false
        val dcId = getDCId(profileActivity).let {
            if (it == -1) "Unknown"
            else "DC$it"
        }
        val context = HostInfo.getApplication()
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("", dcId))
        Toast.makeText(context, LocaleController.getString("IdCopied", R.string.IdCopied), Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onInsertRow(
        manipulator: ProfileActivityRowHook.RowManipulator,
        profileActivity: Any
    ) {
        val row = manipulator.getRowIdForKey(ShowIdInProfile.rowName).let { idr ->
            if (idr != -1) idr + 1
            else {
                manipulator.getRowIdForField("notificationsRow")
                    .let { nr ->
                        if (nr != -1) nr
                        else manipulator.getRowIdForField("infoHeaderRow").let { ihr ->
                            if (ihr == -1) 1 else ihr + 1
                        }
                    }
            }
        }
        manipulator.insertRowAtPosition(rowName, row)
    }

    private fun getDCId(profileActivity: Any): Int = runCatching {
        return XposedHelpers.getObjectField(profileActivity, "userInfo")?.let { uInfo ->
            XposedHelpers.getObjectField(uInfo, "profile_photo")?.let { photo ->
                XposedHelpers.getObjectField(photo, "dc_id") as Int
            }
        } ?: XposedHelpers.getObjectField(profileActivity, "chatInfo")?.let { cInfo ->
            XposedHelpers.getObjectField(cInfo, "chat_photo")?.let { chatPhoto ->
                XposedHelpers.getObjectField(chatPhoto, "dc_id") as Int
            }
        } ?: -1
    }.onFailure {
        Log.e("getDCId", it)
    }.getOrDefault(-1)

    override fun getPriority(): Int = 1
}