package momoi.mod.qqpro.api

import com.tencent.qqnt.kernel.api.impl.GroupService
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberExtReq
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberListResult
import com.tencent.qqnt.kernel.nativeinterface.MemberExtInfoFilter
import com.tencent.qqnt.msg.KernelServiceUtil
import momoi.mod.qqpro.util.Utils

object GroupAPI {
    inline fun getMemberInfo(
        groupId: Long,
        uid: Long,
        crossinline callback: (GroupMemberListResult) -> Unit
    ) {
        KernelServiceUtil.b()?.getMemberInfoForMqq(
            groupId,
            arrayListOf(uid.toString()),
            false
        ) { _, _, result ->
            callback(result)
        }
    }

    /**
     * Fetch real group member levels.
     *
     * `MemberInfo.memberLevel` returned by the member-list APIs is never
     * populated by the kernel (always shows a default), so the actual level
     * lives in `MemberExtInfo.level`, fetched separately via getMemberExtInfo.
     *
     * Returns a map of QQ uin -> level.
     */
    inline fun getMemberLevels(
        groupId: Long,
        uins: List<Long>,
        crossinline callback: (Map<Long, Int>) -> Unit
    ) {
        // The public IGroupService wrapper doesn't expose getMemberExtInfo, so
        // reach the underlying IKernelGroupService via the impl's getService().
        val wrapper = KernelServiceUtil.b()
        Utils.log("GroupAPI.getMemberLevels enter group=$groupId uins=${uins.size} wrapper=${wrapper?.javaClass?.name}")
        val service = (wrapper as? GroupService)?.service
        if (service == null || uins.isEmpty()) {
            Utils.log("GroupAPI.getMemberLevels abort service=$service uins=${uins.size}")
            callback(emptyMap())
            return
        }
        val req = GroupMemberExtReq().apply {
            groupCode = groupId
            sourceType = 1
            uinList = ArrayList(uins)
            memberExtFilter = MemberExtInfoFilter().apply {
                memberLevelInfoUin = 1
                memberLevelInfoLevel = 1
                memberLevelInfoPoint = 1
                memberLevelInfoActiveDay = 1
                levelName = 1
                nickName = 1
            }
        }
        Utils.log("GroupAPI.getMemberLevels calling getMemberExtInfo req groupCode=${req.groupCode} uinList=${req.uinList.size}")
        service.getMemberExtInfo(req) { code, msg, result ->
            Utils.log("GroupAPI.getMemberLevels onResult code=$code msg=$msg result=${result != null} count=${result?.memberLevelInfo?.size}")
            if (code != 0 || result == null) {
                Utils.log("GroupAPI.getMemberLevels failed code=$code msg=$msg")
                callback(emptyMap())
                return@getMemberExtInfo
            }
            val levels = HashMap<Long, Int>()
            result.memberLevelInfo.forEach {
                Utils.log("GroupAPI.getMemberLevels uin=${it.uin} level=${it.level} point=${it.point}")
                levels[it.uin] = it.level
            }
            Utils.log("GroupAPI.getMemberLevels group=$groupId got ${levels.size} levels")
            callback(levels)
        }
    }
}
